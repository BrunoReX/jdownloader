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

import java.util.ArrayList;
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
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

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "1tube.to" }, urls = { "https?://(?:www\\.)?1tube\\.to/f/([^<>\"]*?\\-[A-Za-z0-9]+\\.html|[A-Za-z0-9]+)" }, flags = { 2 })
public class OneTubeTo extends PluginForHost {

    public OneTubeTo(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://1tube.to/reg/");
    }

    @Override
    public String getAGBLink() {
        return "https://1tube.to/p/tos/";
    }

    /* Tags: 1tube.to, hdstream.to */
    /* Connection stuff */
    private static final boolean FREE_RESUME                  = false;
    private static final int     FREE_MAXCHUNKS               = 1;
    private static final int     FREE_MAXDOWNLOADS            = 1;
    private static final boolean ACCOUNT_FREE_RESUME          = false;
    private static final int     ACCOUNT_FREE_MAXCHUNKS       = 1;
    private static final int     ACCOUNT_FREE_MAXDOWNLOADS    = 1;
    private static final boolean ACCOUNT_PREMIUM_RESUME       = true;
    private static final int     ACCOUNT_PREMIUM_MAXCHUNKS    = 1;
    private static final int     ACCOUNT_PREMIUM_MAXDOWNLOADS = 10;

    private static final int     PREMIUM_OVERALL_MAXCON       = -ACCOUNT_PREMIUM_MAXDOWNLOADS;

    private Exception            checklinksexception          = null;

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        final String fid = getFID(link);
        link.setLinkID(fid);
        link.setUrlDownload("https://1tube.to/f/" + fid);
    }

    /** Using API: https://1tube.to/p/api/ */
    @SuppressWarnings({ "unchecked", "deprecation" })
    @Override
    public boolean checkLinks(final DownloadLink[] urls) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        try {
            prepBrowser(br);
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            LinkedHashMap<String, Object> api_data = null;
            LinkedHashMap<String, Object> api_data_singlelink = null;
            int index = 0;
            while (true) {
                links.clear();
                while (true) {
                    /* we test 50 links at once (50 tested, more might be possible) */
                    if (index == urls.length || links.size() > 50) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                sb.append("fun=check&check=");
                for (final DownloadLink dl : links) {
                    sb.append(Encoding.urlEncode(dl.getDownloadURL()));
                    sb.append("\n");
                }
                /* TODO: Implement this correctly! */
                br.postPage("https://1tube.to/p/api/?json=1", sb.toString());
                api_data = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
                api_data = (LinkedHashMap<String, Object>) api_data.get("data");
                for (final DownloadLink dl : links) {
                    final String fid = getFID(dl);
                    api_data_singlelink = (LinkedHashMap<String, Object>) api_data.get(fid);
                    final String state = (String) api_data_singlelink.get("state");
                    if (api_data_singlelink == null || "off".equals(state)) {
                        dl.setName(fid);
                        dl.setAvailable(false);
                        continue;
                    }
                    final String filename = (String) api_data_singlelink.get("name");
                    final long filesize = DummyScriptEnginePlugin.toLong(api_data_singlelink.get("size"), 0);
                    final String sha1 = (String) api_data_singlelink.get("hash");

                    /* Trust API */
                    dl.setAvailable(true);
                    dl.setFinalFileName(filename);
                    dl.setDownloadSize(filesize);
                    dl.setSha1Hash(sha1);
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            checklinksexception = e;
            return false;
        }
        return true;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        checkLinks(new DownloadLink[] { downloadLink });
        if (!downloadLink.isAvailabilityStatusChecked()) {
            return AvailableStatus.UNCHECKED;
        }
        if (downloadLink.isAvailabilityStatusChecked() && !downloadLink.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        /* If exception happens in availablecheck it will be caught --> Browser is empty --> Throw it here to prevent further errors. */
        if (checklinksexception != null) {
            throw checklinksexception;
        }
        jd.plugins.hoster.HdStreamTo.checkDownloadable(this.br);
        doFree(downloadLink, FREE_RESUME, FREE_MAXCHUNKS, "free_directlink");
    }

    @SuppressWarnings("deprecation")
    private void doFree(final DownloadLink downloadLink, boolean resumable, int maxchunks, final String directlinkproperty) throws Exception, PluginException {
        final String fid = this.getFID(downloadLink);
        final String premiumtoken = getPremiumToken(downloadLink);
        String dllink = checkDirectLink(downloadLink, directlinkproperty);
        if (dllink == null) {
            this.br.setFollowRedirects(false);
            this.br.getPage("https://1tube.to/send/?visited=" + fid);
            final String canPlay = getJson("canPlay");
            final String server = getJson("server");
            final String waittime = getJson("wait");
            final String free_downloadable = getJson("downloadable");
            final String free_downloadable_max_filesize = new Regex(free_downloadable, "mb(\\d+)").getMatch(0);
            final String traffic_left_free = getJson("traffic");
            if ("true".equals(canPlay)) {
                /* Prefer to download the stream if possible as it has the same filesize as download but no waittime. */
                final Browser br2 = br.cloneBrowser();
                br2.getPage("https://sx" + server + ".1tube.to/send/?token=" + fid + "&stream=1");
                dllink = br2.getRedirectLocation();
            }
            if (dllink == null) {
                /* Stream impossible? --> Download file! */
                /*
                 * Note that premiumtokens can override this. NOTE that premiumtokens do not (yet) exist for this host (project), see
                 * hdstream.to plugin.
                 */
                if (free_downloadable.equals("premium") || (free_downloadable_max_filesize != null && downloadLink.getDownloadSize() >= SizeFormatter.getSize(free_downloadable_max_filesize + " mb")) && premiumtoken.equals("")) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
                } else if (traffic_left_free.equals("0")) {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED);
                }
                dllink = getDllink(downloadLink);
                if (waittime == null) {
                    /* This should never happen! */
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                final int wait = Integer.parseInt(waittime);
                /* Make sure that the premiumtoken is valid - if it is not valid, wait is higher than 0 */
                if (!premiumtoken.equals("") && wait == 0) {
                    logger.info("Seems like the user is using a valid premiumtoken, enabling chunks & resume...");
                    resumable = ACCOUNT_PREMIUM_RESUME;
                    maxchunks = PREMIUM_OVERALL_MAXCON;
                } else {
                    this.sleep(Integer.parseInt(waittime) * 1001l, downloadLink);
                }
            }
        }
        this.br.setFollowRedirects(true);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resumable, maxchunks);
        jd.plugins.hoster.HdStreamTo.errorhandlingFree(this.dl, this.br);
        downloadLink.setProperty(directlinkproperty, dllink);
        dl.startDownload();
    }

    private static final String MAINPAGE = "http://1tube.to";
    private static Object       LOCK     = new Object();

    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                prepBrowser(this.br);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null && !force) {
                    this.br.setCookies(this.getHost(), cookies);
                    return;
                }
                br.setFollowRedirects(false);
                br.postPage("https://1tube.to/json/login.php", "data=%7B%22username%22%3A%22" + Encoding.urlEncode(account.getUser()) + "%22%2C+%22password%22%3A%22" + Encoding.urlEncode(account.getPass()) + "%22%7D");
                if (br.getCookie(MAINPAGE, "username") == null || br.containsHTML("\"logged_in\":false")) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.saveCookies(this.br.getCookies(this.getHost()), "");
            } catch (final PluginException e) {
                account.clearCookies("");
                throw e;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        try {
            login(account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        return jd.plugins.hoster.HdStreamTo.fetchAccountInfoHdstream(this.br, account);
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        jd.plugins.hoster.HdStreamTo.checkDownloadable(this.br);
        login(account, false);
        br.setFollowRedirects(false);
        if (account.getType() == AccountType.FREE) {
            doFree(link, ACCOUNT_FREE_RESUME, ACCOUNT_FREE_MAXCHUNKS, "account_free_directlink");
        } else {
            final String dllink = getDllink(link);
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, ACCOUNT_PREMIUM_RESUME, ACCOUNT_PREMIUM_MAXCHUNKS);
            jd.plugins.hoster.HdStreamTo.errorhandlingPremium(this.dl, this.br, account);
            link.setProperty("premium_directlink", dllink);
            dl.startDownload();
        }
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                con = br2.openHeadConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return dllink;
    }

    /** Returns final downloadlink, same for free and premium */
    private String getDllink(final DownloadLink dl) {
        return "http://sx" + getJson("server") + ".1tube.to/send.php?token=" + getFID(dl);
    }

    /**
     * Links which contain a premium token can be downloaded via free like a premium user - in case such a token exists in a link, this
     * function will return it.
     *
     * @return: "" (empty String) if there is no token and the token if there is one
     */
    @SuppressWarnings("deprecation")
    private String getPremiumToken(final DownloadLink dl) {
        final String addedlink = dl.getDownloadURL();
        String premtoken = new Regex(addedlink, "hdstream\\.to/(f/|#\\!f=)[A-Za-z0-9]+\\-([A-Za-z0-9]+)$").getMatch(1);
        if (premtoken == null) {
            premtoken = "";
        }
        return premtoken;
    }

    private String getFID(final DownloadLink dl) {
        final String fid = new Regex(dl.getDownloadURL(), "([A-Za-z0-9]+)(?:\\.html)?$").getMatch(0);
        return fid;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return FREE_MAXDOWNLOADS;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return ACCOUNT_PREMIUM_MAXDOWNLOADS;
    }

    private void prepBrowser(final Browser br) {
        br.setCookie("http://hdstream.to/", "lang", "en");
        /* User can select https or http in his hdstream account, therefore, redirects should be allowed */
        br.setFollowRedirects(true);
        br.getHeaders().put("User-Agent", "JDownloader");
    }

    /**
     * Wrapper<br/>
     * Tries to return value of key from JSon response, from default 'br' Browser.
     *
     * @author raztoki
     */
    protected final String getJson(final String key) {
        return jd.plugins.hoster.K2SApi.JSonUtils.getJson(br.toString(), key);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}