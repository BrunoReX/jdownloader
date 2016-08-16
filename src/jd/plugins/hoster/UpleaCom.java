//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "uplea.com" }, urls = { "http://(www\\.)?uplea\\.com/dl/[A-Z0-9]+" }, flags = { 2 })
public class UpleaCom extends antiDDoSForHost {

    @Override
    protected boolean useRUA() {
        return true;
    }

    public UpleaCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.uplea.com/register");
    }

    @Override
    public String getAGBLink() {
        return "http://www.uplea.com/register";
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        getPage(link.getDownloadURL());
        if (br.containsHTML(">You followed an invalid or expired link")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        // filename needs to be able to catch filenames with @ which are obstructed via cloudflare
        String filename = br.getRegex("class=\"(?:agmd size18|gold-text)\">(.*?)</span>").getMatch(0);
        String filesize = br.getRegex("class=\"label label-info agmd(?: size14)?\">([^<>\"]*?)</span>").getMatch(0);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filesize = filesize.replace("ko", "KB");
        filesize = filesize.replace("Mo", "MB");
        filesize = filesize.replace("Go", "GB");
        if (StringUtils.contains(filename, "__cf_email")) {
            // possible to be either of these three combinations, prefix, email, postfix
            final String prefix = new Regex(filename, "(.+\\.)<a ").getMatch(0);
            final String email = getStringFromCloudFlareEmailProtection(filename);
            final String postfix = new Regex(filename, "</script>(.+)").getMatch(0);
            filename = (prefix != null ? prefix : "") + email + (postfix != null ? postfix : "");
        }
        link.setName((Encoding.htmlDecode(filename.trim())));
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink);
    }

    private void doFree(final DownloadLink downloadLink) throws Exception, PluginException {
        final Browser brad = br.cloneBrowser();
        final String fid = getFID(downloadLink);
        final String free_link = "http://uplea.com/step/" + fid + "/3";
        brad.getHeaders().put("X-Requested-With", "XMLHttpRequest");

        try {
            getPage(brad.cloneBrowser(), "http://uplea.com/socials/" + fid);
            brad.getHeaders().put("Referer", free_link);
            postPage(brad, "http://uplea.com/ajax/web-init", "state=true");
        } catch (final Throwable e) {
        }

        getPage(free_link);
        final String wait_seconds = br.getRegex("timeText:(\\d+)").getMatch(0);
        if (wait_seconds != null) {
            final int intwaitsecs = Integer.parseInt(wait_seconds);
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, intwaitsecs * 1001l);
        } else if (br.containsHTML("class=\"premium_title_exceed\"")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED);
        }
        br.setFollowRedirects(false);
        final String dllink = br.getRegex("\"(https?://[a-z0-9]+\\.uplea\\.com/(anonym|free)/[^<>\"]*?)\"").getMatch(0);
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }

        try {
            getPage(brad.cloneBrowser(), "http://uplea.com/socials/" + fid);
            brad.getHeaders().put("Referer", free_link);
            postPage(brad, "http://uplea.com/ajax/web-init", "state=true");
        } catch (final Throwable e) {
        }

        final String wait = br.getRegex("ulCounter\\(\\{'timer':(\\d+)\\}\\)").getMatch(0);
        int waitt = 10;
        if (wait != null) {
            waitt = Integer.parseInt(wait);
        }
        this.sleep(waitt * 1001l, downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            handleServerErrors();
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 5 * 60 * 1000l);
        }
        dl.startDownload();
    }

    final String getFID(final DownloadLink dl) {
        return new Regex(dl.getDownloadURL(), "([A-Z0-9]+)$").getMatch(0);
    }

    private void handleServerErrors() throws PluginException {
        if (br.containsHTML("Invalid Link")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error ('invalid link')");
        }
    }

    private static final String MAINPAGE = "http://uplea.com";
    private static Object       LOCK     = new Object();

    @SuppressWarnings("unchecked")
    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            br.setCookie(MAINPAGE, key, value);
                        }
                        return;
                    }
                }
                br.setFollowRedirects(false);
                postPage("http://uplea.com/?lang=en", "remember=1&login-form=&login=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
                if (br.getCookie(MAINPAGE, "uplea") == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", fetchCookies(MAINPAGE));
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        try {
            login(account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        getPage("/account");
        ai.setUnlimitedTraffic();
        String expire = br.getRegex("You're premium member until  <span class=\"cyan\">([^<>\"]*?) \\(").getMatch(0);
        if (expire == null) {
            expire = br.getRegex("(\\d{2}/\\d{2}/\\d{4}) \\(\\d+ days\\(s\\) ").getMatch(0);
        }
        if (expire != null) {
            ai.setValidUntil(TimeFormatter.getMilliSeconds(expire, "dd/MM/yyyy", Locale.ENGLISH));
            account.setType(AccountType.PREMIUM);
            ai.setStatus("Premium Account");
        } else {
            account.setType(AccountType.FREE);
            ai.setStatus("Free Account");
        }
        account.setValid(true);
        return ai;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account, false);
        br.setFollowRedirects(false);
        getPage(link.getDownloadURL());
        if (AccountType.FREE.equals(account.getType())) {
            doFree(link);
        } else {
            final String dllink = br.getRegex("\"(https?://[a-z0-9]+\\.uplea.com/premium/[^<>\"]*?)\"").getMatch(0);
            if (dllink == null) {
                logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, Encoding.htmlDecode(dllink), false, 1);
            if (dl.getConnection().getContentType().contains("html")) {
                logger.warning("The final dllink seems not to be a file!");
                br.followConnection();
                handleServerErrors();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        }
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}