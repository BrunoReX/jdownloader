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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
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
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "pinterest.com" }, urls = { "https?://(?:(?:www|[a-z]{2})\\.)?pinterest\\.com/pin/\\d+/" }, flags = { 2 })
public class PinterestCom extends PluginForHost {

    public PinterestCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www.pinterest.com/");
        setConfigElements();
    }

    @Override
    public String getAGBLink() {
        return "https://about.pinterest.com/de/terms-service";
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        /* Correct link - remove country related language-subdomains (e.g. 'es.pinterest.com'). */
        final String pin = new Regex(link.getDownloadURL(), "(\\d+)/$").getMatch(0);
        link.setUrlDownload("https://www.pinterest.com/pin/" + pin + "/");
    }

    /* Connection stuff */
    private static final boolean FREE_RESUME               = false;
    private static final int     FREE_MAXCHUNKS            = 1;
    private static final int     FREE_MAXDOWNLOADS         = 20;
    private static final boolean ACCOUNT_FREE_RESUME       = false;
    private static final int     ACCOUNT_FREE_MAXCHUNKS    = 1;
    private static final int     ACCOUNT_FREE_MAXDOWNLOADS = 20;

    /* Site constants */
    public static final String   x_app_version             = "9491270";
    public static final String   default_extension         = ".jpg";

    /* don't touch the following! */
    private static AtomicInteger maxPrem                   = new AtomicInteger(1);
    private String               dllink                    = null;

    @SuppressWarnings({ "deprecation", "unchecked", "rawtypes" })
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        String filename = null;
        final String pin_id = new Regex(link.getDownloadURL(), "(\\d+)/?$").getMatch(0);
        /* Display ids for offline links */
        link.setName(pin_id);
        try {
            link.setLinkID(pin_id);
        } catch (final Throwable e) {
            /* Not available in old 0.9.581 Stable */
            link.setProperty("LINKDUPEID", pin_id);
        }
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        String site_title = null;
        dllink = checkDirectLink(link, "free_directlink");
        if (dllink != null) {
            /* Avoid unnecessary site requests. */
            site_title = link.getFinalFileName();
            if (site_title == null) {
                site_title = pin_id;
            }
        } else {
            final String source_url = link.getStringProperty("source_url", null);
            final String boardid = link.getStringProperty("boardid", null);
            final String username = link.getStringProperty("username", null);
            final Account aa = AccountController.getInstance().getValidAccount(this);
            if (aa != null && source_url != null && boardid != null && username != null) {
                login(this.br, aa, false);
                String pin_ressource_url = "http://www.pinterest.com/resource/PinResource/get/?source_url=";
                String options = "/pin/%s/&data={\"options\":{\"field_set_key\":\"detailed\",\"link_selection\":true,\"fetch_visual_search_objects\":true,\"id\":\"%s\"},\"context\":{},\"module\":{\"name\":\"CloseupContent\",\"options\":{\"unauth_pin_closeup\":false}},\"render_type\":1}&module_path=App()>BoardPage(resource=BoardResource(username=amazvicki,+slug=))>Grid(resource=BoardFeedResource(board_id=%s,+board_url=%s,+page_size=null,+prepend=true,+access=,+board_layout=default))>GridItems(resource=BoardFeedResource(board_id=%s,+board_url=%s,+page_size=null,+prepend=true,+access=,+board_layout=default))>Pin(show_pinner=false,+show_pinned_from=true,+show_board=false,+squish_giraffe_pins=false,+component_type=0,+resource=PinResource(id=%s))";
                options = String.format(options, pin_id, pin_id, username, username, boardid, source_url, boardid, source_url, pin_id);
                options = options.replace("/", "%2F");
                // options = Encoding.urlEncode(options);
                pin_ressource_url += options;
                pin_ressource_url += "&_=" + System.currentTimeMillis();

                prepAPIBR(this.br);
                br.getPage(pin_ressource_url);
                if (isOffline(this.br, pin_id)) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
                final LinkedHashMap<String, Object> page_info = (LinkedHashMap<String, Object>) entries.get("page_info");
                final ArrayList<Object> ressourcelist = (ArrayList) entries.get("resource_data_cache");
                dllink = getDirectlinkFromJson(ressourcelist, pin_id);
                site_title = (String) page_info.get("title");
                /* We don't have to be logged in to perform downloads so better log out to avoid account bans. */
                br.clearCookies(MAINPAGE);
            } else {
                br.getPage(link.getDownloadURL());
                if (isOffline(this.br, pin_id)) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                /*
                 * Site actually contains similar json compared to API --> Grab that and get the final link via that as it is not always
                 * present in the normal html code.
                 */
                String json = br.getRegex("P\\.(?:start\\.start|main\\.start)\\((.*?)\\);\n").getMatch(0);
                if (json == null) {
                    json = br.getRegex("P\\.startArgs = (.*?);\n").getMatch(0);
                }
                if (json == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(json);
                final ArrayList<Object> ressourcelist = (ArrayList) entries.get("resourceDataCache");
                dllink = getDirectlinkFromJson(ressourcelist, pin_id);
                site_title = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
            }
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            link.setProperty("free_directlink", dllink);
            /* Check if our directlink is actually valid. */
            dllink = checkDirectLink(link, "free_directlink");
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            site_title = Encoding.htmlDecode(site_title).trim();
            site_title = pin_id + "_" + site_title;
        }
        if (site_title != null && link.getComment() == null) {
            link.setComment(site_title);
        }
        String ext = dllink.substring(dllink.lastIndexOf("."));
        if (ext == null || ext.length() > 5) {
            ext = default_extension;
        }
        filename = pin_id;
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        filename = encodeUnicode(filename);
        link.setFinalFileName(filename);
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings("unchecked")
    private String getDirectlinkFromJson(final ArrayList<Object> ressourcelist, final String pin_id) {
        String directlink = null;
        for (final Object resource_object : ressourcelist) {
            final LinkedHashMap<String, Object> t2 = (LinkedHashMap<String, Object>) resource_object;
            final LinkedHashMap<String, Object> t3 = (LinkedHashMap<String, Object>) t2.get("data");
            final String this_pin_id = (String) t3.get("id");
            if (this_pin_id.equals(pin_id)) {
                final LinkedHashMap<String, Object> t4 = (LinkedHashMap<String, Object>) t3.get("images");
                final LinkedHashMap<String, Object> t5 = (LinkedHashMap<String, Object>) t4.get("orig");
                directlink = (String) t5.get("url");
                break;
            }
        }
        return directlink;
    }

    public static void prepAPIBR(final Browser br) throws PluginException {
        final String csrftoken = br.getCookie(MAINPAGE, "csrftoken");
        if (csrftoken == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getHeaders().put("X-Pinterest-AppState", "active");
        br.getHeaders().put("X-NEW-APP", "1");
        br.getHeaders().put("X-APP-VERSION", x_app_version);
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        br.getHeaders().put("X-CSRFToken", csrftoken);
    }

    private static boolean isOffline(final Browser br, final String pin_id) {
        if (br.getHttpConnection().getResponseCode() == 404) {
            return true;
        } else if (!br.getURL().contains(pin_id)) {
            /* Pin redirected to other pin --> initial pin is offline! */
            return true;
        }
        return false;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, FREE_RESUME, FREE_MAXCHUNKS, "free_directlink");
    }

    private void doFree(final DownloadLink downloadLink, final boolean resumable, final int maxchunks, final String directlinkproperty) throws Exception, PluginException {
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resumable, maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty(directlinkproperty, dllink);
        dl.startDownload();
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                con = br2.openGetConnection(dllink);
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

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return FREE_MAXDOWNLOADS;
    }

    private static final String MAINPAGE = "http://pinterest.com";
    private static Object       LOCK     = new Object();

    @SuppressWarnings("unchecked")
    public static void login(final Browser br, final Account account, final boolean force) throws Exception {
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
                br.setFollowRedirects(true);
                br.getPage("https://www.pinterest.com/login/?action=login");
                prepAPIBR(br);
                String postData = "source_url=/login/&data={\"options\":{\"username_or_email\":\"" + account.getUser() + "\",\"password\":\"" + account.getPass() + "\"},\"context\":{}}&module_path=App()>LoginPage()>Login()>Button(class_name=primary,+text=Anmelden,+type=submit,+size=large)";
                // postData = Encoding.urlEncode(postData);
                try {
                    br.postPageRaw("https://www.pinterest.com/resource/UserSessionResource/create/", postData);
                } catch (final BrowserException e) {
                    if (br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 401) {
                        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                    throw e;
                }
                if (br.containsHTML("jax CsrfErrorPage Module") || br.getCookie(MAINPAGE, "_b") == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.setProperty("free", true);
                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = br.getCookies(MAINPAGE);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(this.br, account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        ai.setUnlimitedTraffic();
        maxPrem.set(ACCOUNT_FREE_MAXDOWNLOADS);
        try {
            account.setType(AccountType.FREE);
            /* free accounts can still have captcha */
            account.setMaxSimultanDownloads(maxPrem.get());
            account.setConcurrentUsePossible(false);
        } catch (final Throwable e) {
            /* not available in old Stable 0.9.581 */
        }
        ai.setStatus("Registered (free) user");
        account.setValid(true);
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        /* We already logged in in requestFileInformation */
        br.setFollowRedirects(false);
        doFree(link, ACCOUNT_FREE_RESUME, ACCOUNT_FREE_MAXCHUNKS, "account_free_directlink");
    }

    /** Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "⁄");
        output = output.replace("\\", "∖");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /* workaround for free/premium issue on stable 09581 */
        return maxPrem.get();
    }

    @Override
    public String getDescription() {
        return "JDownloader's Pornhub plugin helps downloading pictures from pinterest.com.";
    }

    public static final String  ENABLE_DESCRIPTION_IN_FILENAMES        = "ENABLE_DESCRIPTION_IN_FILENAMES";
    public static final boolean defaultENABLE_DESCRIPTION_IN_FILENAMES = false;

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ENABLE_DESCRIPTION_IN_FILENAMES, JDL.L("plugins.hoster.PinterestCom.enableDescriptionInFilenames", "Add pind-escription to filenames?\r\nNOTE: If enabled, Filenames might get very long!")).setDefaultValue(defaultENABLE_DESCRIPTION_IN_FILENAMES));
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}