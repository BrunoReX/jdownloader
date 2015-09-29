//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
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
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "uploadable.ch" }, urls = { "https?://(?:www\\.)?uploadable\\.ch/file/[A-Za-z0-9]+" }, flags = { 2 })
public class UploadableCh extends PluginForHost {

    public UploadableCh(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.uploadable.ch/extend.php");
        this.setConfigElements();
    }

    @Override
    public String getAGBLink() {
        return "http://www.uploadable.ch/terms.php";
    }

    private static final long   FREE_SIZELIMIT          = 2 * 1073741824l;
    private static final String PREMIUM_UNLIMITEDCHUNKS = "PREMIUM_UNLIMITEDCHUNKS";

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        /* Forced https */
        link.setUrlDownload(link.getDownloadURL().replace("http://", "https://"));
    }

    private String getProtocol() {
        return "https://";
    }

    @Override
    public boolean checkLinks(final DownloadLink[] urls) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        try {
            final Browser br = new Browser();
            prepBr(br);
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            while (true) {
                links.clear();
                while (true) {
                    /* we test 50 links at once */
                    if (index == urls.length || links.size() > 50) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                sb.append("urls=");
                for (final DownloadLink dl : links) {
                    sb.append(dl.getDownloadURL());
                    sb.append("%0A");
                }
                br.postPage(getProtocol() + "www.uploadable.ch/check.php", sb.toString());
                for (final DownloadLink dllink : links) {
                    final String fid = new Regex(dllink.getDownloadURL(), "/file/([A-Za-z0-9]+)$").getMatch(0);
                    final String linkinfo = br.getRegex("href=\"\">(http://(www\\.)?uploadable\\.ch/file/" + fid + "</a></div>.*?)</li>").getMatch(0);
                    if (linkinfo == null) {
                        logger.warning("Mass-Linkchecker broken");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    } else if (linkinfo.contains(">Not Available<")) {
                        dllink.setAvailable(false);
                    } else {
                        final String name = new Regex(linkinfo, "class=\"col2\">([^<>\"]*?)</div>").getMatch(0);
                        final String size = new Regex(linkinfo, "class=\"col3\">([^<>\"]*?)</div>").getMatch(0);
                        dllink.setAvailable(true);
                        dllink.setName(Encoding.htmlDecode(name.trim()));
                        dllink.setDownloadSize(SizeFormatter.getSize(size));
                    }
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            return false;
        }
        return true;
    }

    private void prepBr(final Browser br) {
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:28.0) Gecko/20100101 Firefox/28.0");
    }

    /** Don't use mass-linkchecker here as it may return wrong/outdated information. */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        correctDownloadLink(link);
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        prepBr(this.br);
        br.getPage(link.getDownloadURL());
        if (br.containsHTML(">File not available<|>This file is no longer available.<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.getURL().contains("error_code=617")) {
            /* >File is not available. Please check your link again.< */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String filename = br.getRegex("id=\"file_name\" title=\"([^<>\"]*?)\"").getMatch(0);
        final String filesize = br.getRegex("class=\"filename_normal\">\\(([^<>\"]*?)\\)</span>").getMatch(0);
        if (filename == null || filesize == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(Encoding.htmlDecode(filename.trim()));
        final long fsize = SizeFormatter.getSize(filesize);
        link.setDownloadSize(fsize);
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, null);
    }

    @SuppressWarnings("deprecation")
    private void doFree(final DownloadLink downloadLink, final Account account) throws Exception {
        if (checkShowFreeDialog(getHost())) {
            showFreeDialog(getHost());
        }
        br.setFollowRedirects(false);
        String dllink = checkDirectLink(downloadLink, "uploadabledirectlink");
        if (dllink == null) {
            final String fid = new Regex(downloadLink.getDownloadURL(), "([A-Za-z0-9]+)$").getMatch(0);
            final String postLink = br.getURL();
            {
                final Browser json = br.cloneBrowser();
                json.getHeaders().put("Accept", "application/json, text/javascript, */*");
                json.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                json.cloneBrowser().getPage("/now.php");
            }
            br.postPage(postLink, "downloadLink=wait");
            int wait = 90;
            final String waittime = br.getRegex("\"waitTime\":(\\d+)").getMatch(0);
            if (waittime != null) {
                wait = Integer.parseInt(waittime);
            }
            sleep(wait * 1001l, downloadLink);
            br.postPage(postLink, "checkDownload=check");
            if (br.containsHTML("\"fail\":\"timeLimit\"")) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, 20 * 60 * 1001l);
            }
            boolean captchaFailed = true;
            final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
            final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
            rc.setId("6LdlJuwSAAAAAPJbPIoUhyqOJd7-yrah5Nhim5S3");
            rc.load();
            for (int i = 1; i <= 5; i++) {
                final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                final String c = getCaptchaCode("recaptcha", cf, downloadLink);
                br.postPage("/checkReCaptcha.php", "recaptcha_challenge_field=" + rc.getChallenge() + "&recaptcha_response_field=" + Encoding.urlEncode(c) + "&recaptcha_shortencode_field=" + fid);
                if (br.containsHTML("\"success\":0") || br.toString().trim().equals("[]")) {
                    rc.reload();
                    continue;
                }
                captchaFailed = false;
                break;
            }
            if (captchaFailed) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }

            br.getHeaders().put("Accept", "*/*");
            br.getHeaders().put("Accept-Language", "de,en-us;q=0.7,en;q=0.3");
            br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            br.getHeaders().put("Referer", postLink);
            br.postPage("/file/" + fid, "downloadLink=show");
            if (br.containsHTML("fail")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 5 * 60 * 1000l);
            }
            br.postPage("/file/" + fid, "download=normal");

            final String reconnect_mins = br.getRegex(">Please wait for (\\d+) minutes  to download the next file").getMatch(0);
            if (reconnect_mins != null) {
                logger.info("uploadable.ch: Reconnect limit detected");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, Integer.parseInt(reconnect_mins) * 60 * 1001l);
            }

            dllink = br.getRedirectLocation();
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.info("Finallink does not lead to a file, continuing...");
            br.followConnection();
            /* Error-links: http://www.uploadable.ch/l-error.php?error_code=ERRORCODE */
            /* Your download link has expired */
            if (br.containsHTML("error_code=1702")) {
                downloadLink.setProperty("uploadabledirectlink", null);
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Your download link has expired'", 1 * 60 * 1000l);
            } else if (br.containsHTML("error_code=1703")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 406", 5 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty("uploadabledirectlink", dllink);
        dl.startDownload();
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    private static final String MAINPAGE = "http://uploadable.ch";
    private static Object       LOCK     = new Object();

    @SuppressWarnings({ "unchecked", "deprecation" })
    private AccountInfo login(final Account account, final boolean force, final AccountInfo ac) throws Exception {
        synchronized (LOCK) {
            final AccountInfo ai = ac != null ? ac : new AccountInfo();
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                prepBr(this.br);
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
                        // lets do a check!
                        final Browser test = br.cloneBrowser();
                        test.setFollowRedirects(false);
                        test.getPage("/");
                        if (!isNotLoggedIn(test, account)) {
                            return ai;
                        }
                    }
                }
                br.setFollowRedirects(false);
                br.postPage(getProtocol() + "www.uploadable.ch/login.php", "autoLogin=on&action__login=normalLogin&userName=" + Encoding.urlEncode(account.getUser()) + "&userPassword=" + Encoding.urlEncode(account.getPass()));
                if (isNotLoggedIn(br, account)) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                br.getPage("/indexboard.php");
                final String space = br.getRegex(">Storage</div>[\t\r\n ]+<div class=\"b_blue_type\">\\s*([^\"/]*?)\\s*</span>").getMatch(0);
                if (space != null) {
                    ai.setUsedSpace(space.trim().replace("<span>", ""));
                }
                final String expiredate = br.getRegex("lass=\"grey_type\">[\r\n\t ]+Until\\s*([^<>\"]*?)\\s*</div>").getMatch(0);
                if (expiredate == null) {
                    // free accounts can still have captcha.
                    account.setMaxSimultanDownloads(1);
                    account.setConcurrentUsePossible(false);
                    account.setType(AccountType.FREE);
                    ai.setStatus("Free Account");
                } else {
                    ai.setValidUntil(TimeFormatter.getMilliSeconds(expiredate.trim(), "dd MMM yyyy", Locale.ENGLISH) + (24 * 60 * 60 * 1000l));
                    account.setMaxSimultanDownloads(20);
                    account.setConcurrentUsePossible(true);
                    account.setType(AccountType.PREMIUM);
                    ai.setStatus("Premium Account");
                }
                ai.setUnlimitedTraffic();
                account.setValid(true);

                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = br.getCookies(MAINPAGE);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
                if (ac == null) {
                    account.setAccountInfo(ai);
                }
                return ai;
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    private void doesPasswordNeedChanging(final Browser br, final Account account) throws IOException, PluginException {
        // test to confirm that user password doesn't need changing
        if (StringUtils.endsWithCaseInsensitive(br.getRedirectLocation(), "/account.php")) {
            br.getPage(br.getRedirectLocation());
            if (br.containsHTML("<div>For security measures, we ask you to update your password\\.</div>")) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "Service provider asks that you update your passsword.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unknown error! Please report to JDownloader Development Team.");
        }
    }

    private boolean isNotLoggedIn(final Browser br, final Account account) throws IOException, PluginException {
        doesPasswordNeedChanging(br, account);
        return br.getCookie(MAINPAGE, "autologin") == null || StringUtils.containsIgnoreCase(br.getCookie(MAINPAGE, "autologin"), "deleted") || !br.containsHTML("class=\"icon logout\"");
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        try {
            return login(account, true, new AccountInfo());
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account, false, null);
        if (account.getType() == AccountType.FREE) {
            doFree(link, account);
        } else {
            br.setFollowRedirects(false);
            /* This way we don't have to care about the users' "instant download" setting */
            String postlink = link.getDownloadURL();
            if (!postlink.contains("http://www.")) {
                postlink = postlink.replace("http://", getProtocol() + "www.");
            }
            br.postPage(postlink, "download=premium");
            /*
             * Full message: You have exceeded your download limit. Please verify your email address to continue downloading.
             */
            if (br.containsHTML("You have exceeded your download limit")) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "Downloadlimit reached", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
            final String dllink = br.getRedirectLocation();
            if (dllink == null) {
                logger.warning("Final link is null");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            int maxchunks = 1;
            if (this.getPluginConfig().getBooleanProperty(PREMIUM_UNLIMITEDCHUNKS, false)) {
                logger.info("User is allowed to use more than 1 chunk in premiummode");
                maxchunks = 0;
            }
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, maxchunks);
            if (dl.getConnection().getContentType().contains("html")) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        }
    }

    @SuppressWarnings("deprecation")
    public boolean canHandle(DownloadLink downloadLink, Account account) {
        if ((account == null || account.getType() == AccountType.FREE) && downloadLink.getDownloadSize() > FREE_SIZELIMIT) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        }
        if (acc.getType() == AccountType.FREE) {
            /* free accounts also have captchas */
            return true;
        }
        return false;
    }

    private void setConfigElements() {
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), PREMIUM_UNLIMITEDCHUNKS, JDL.L("plugins.hoster.uploadablech.allowPremiumUnlimitedChunks", "Allow unlimited (=20) chunks for premium mode [may cause issues]?")).setDefaultValue(false));
    }
}