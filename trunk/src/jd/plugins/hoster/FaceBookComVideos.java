//    jDownloader - Downloadmanager
//    Copyright (C) 2012  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

import org.appwork.storage.simplejson.JSonUtils;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "facebook.com" }, urls = { "https?://(?:www\\.)?facebookdecrypted\\.com/(video\\.php\\?v=|photo\\.php\\?fbid=|download/)\\d+" }, flags = { 2 })
public class FaceBookComVideos extends PluginForHost {

    private String              FACEBOOKMAINPAGE      = "http://www.facebook.com";
    private String              PREFERHD              = "PREFERHD";
    private static final String TYPE_SINGLE_PHOTO     = "https?://(www\\.)?facebook\\.com/photo\\.php\\?fbid=\\d+";
    private static final String TYPE_SINGLE_VIDEO_ALL = "https?://(www\\.)?facebook\\.com/video\\.php\\?v=\\d+";
    private static final String TYPE_DOWNLOAD         = "https?://(www\\.)?facebook\\.com/download/\\d+";
    private static final String REV                   = jd.plugins.decrypter.FaceBookComGallery.REV;

    private static Object       LOCK                  = new Object();
    private boolean             pluginloaded          = false;

    private String              DLLINK                = null;
    private boolean             loggedIN              = false;
    private boolean             accountNeeded         = false;

    private int                 MAXCHUNKS             = 0;
    private boolean             is_private            = false;

    public FaceBookComVideos(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.facebook.com/r.php");
        setConfigElements();
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("facebookdecrypted.com/", "facebook.com/"));
        String thislink = link.getDownloadURL().replace("https://", "http://");
        String videoID = new Regex(thislink, "facebook\\.com/video\\.php\\?v=(\\d+)").getMatch(0);
        if (videoID != null) {
            thislink = "http://facebook.com/video.php?v=" + videoID;
        }
        link.setUrlDownload(thislink);
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    @SuppressWarnings({ "deprecation", "unchecked" })
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        is_private = link.getBooleanProperty("is_private", false);
        DLLINK = link.getStringProperty("directlink", null);
        link.setName(new Regex(link.getDownloadURL(), "(\\d+)$").getMatch(0));
        br.setCookie("http://www.facebook.com", "locale", "en_GB");
        br.setFollowRedirects(true);
        final String lid = new Regex(link.getDownloadURL(), "(\\d+)$").getMatch(0);
        final Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa != null && aa.isValid()) {
            login(aa, br);
            loggedIN = true;
        }
        String filename = null;
        URLConnectionAdapter con = null;
        if (link.getDownloadURL().matches(TYPE_SINGLE_PHOTO) && is_private) {
            accountNeeded = true;
            if (!loggedIN) {
                return AvailableStatus.UNCHECKABLE;
            }
            this.br.getPage(FACEBOOKMAINPAGE);
            final String user = getUser(this.br);
            final String image_id = getPICID(link);
            final String thread_fbid = link.getStringProperty("thread_fbid", null);
            final String tmp_postdata = "__user=" + user + "&__a=1&__dyn=" + jd.plugins.decrypter.FaceBookComGallery.getDyn() + "&__req=f&fb_dtsg=" + jd.plugins.decrypter.FaceBookComGallery.getfb_dtsg() + "&ttstamp=" + System.currentTimeMillis() + "&__rev=" + jd.plugins.decrypter.FaceBookComGallery.getRev(this.br);
            this.br.postPage("https://www.facebook.com/ajax/messaging/attachments/sharedphotos.php?thread_id=" + thread_fbid + "&image_id=" + image_id, tmp_postdata);
            final String json = this.br.getRegex("for \\(;;\\);(\\{.+)").getMatch(0);
            if (json == null) {
                /* No json? Probably our url is offline! */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(json);
            /**
             * TODO: Change the walkString to only one:
             * "jsmods/require/{0}/{3}/{1}/query_results/{1}/message_images/edges/{0}/node/image1/uri"
             *
             * Should work fine but needs to be tested first!
             * */
            entries = (LinkedHashMap<String, Object>) DummyScriptEnginePlugin.walkJson(entries, "jsmods/require/{0}/{3}/{1}/query_results");
            final Iterator<Entry<String, Object>> it_temp = entries.entrySet().iterator();
            final String id_temp = it_temp.next().getKey();
            final String walkstring = id_temp + "/message_images/edges/{0}/node/image1/uri";
            final String finallink = (String) DummyScriptEnginePlugin.walkJson(entries, walkstring);
            if (finallink == null) {
                /* Something website-wise has changed! */
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            MAXCHUNKS = 1;
            try {
                DLLINK = link.getDownloadURL();
                con = br.openHeadConnection(DLLINK);
                if (!con.getContentType().contains("html")) {
                    filename = Encoding.htmlDecode(getFileNameFromHeader(con));
                    link.setDownloadSize(con.getLongContentLength());
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
        } else if (link.getDownloadURL().matches(TYPE_DOWNLOAD)) {
            MAXCHUNKS = 1;
            try {
                DLLINK = link.getDownloadURL();
                con = br.openGetConnection(DLLINK);
                if (!con.getContentType().contains("html")) {
                    filename = Encoding.htmlDecode(getFileNameFromHeader(con));
                    link.setDownloadSize(con.getLongContentLength());
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
        } else {
            br.getPage(link.getDownloadURL());
            if (!br.containsHTML("class=\"uiStreamPrivacy inlineBlock fbStreamPrivacy fbPrivacyAudienceIndicator\"") && !loggedIN) {
                accountNeeded = true;
                link.setName(new Regex(link.getDownloadURL(), "(\\d+)$").getMatch(0));
                link.getLinkStatus().setStatusText(JDL.L("plugins.hoster.facebookvideos.only4registered", "Links can only be checked if a valid account is entered"));
                return AvailableStatus.UNCHECKABLE;
            }
            String getThisPage = br.getRegex("window\\.location\\.replace\\(\"(http:.*?)\"").getMatch(0);
            if (getThisPage != null) {
                br.getPage(getThisPage.replace("\\", ""));
            }
            if (this.br.getHttpConnection().getResponseCode() == 404) {
                link.setFinalFileName(link.getDownloadURL());
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (loggedIN) {
                filename = br.getRegex("id=\"pageTitle\">([^<>\"]*?)</title>").getMatch(0);
                if (filename == null) {
                    filename = br.getRegex("class=\"mtm mbs mrs fsm fwn fcg\">[A-Za-z0-9:]+</span>([^<>\"]*?)</div>").getMatch(0);
                }
            } else {
                filename = br.getRegex("id=\"pageTitle\">([^<>\"]*?)\\| Facebook</title>").getMatch(0);
                if (filename == null) {
                    filename = br.getRegex("id=\"pageTitle\">([^<>\"]*?)</title>").getMatch(0);
                }
            }
            if (filename == null) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            filename = Encoding.htmlDecode(filename.trim());

            if (link.getDownloadURL().matches(TYPE_SINGLE_PHOTO)) {
                // Try if a downloadlink is available
                DLLINK = br.getRegex("href=\"(https?://[^<>\"]*?(\\?|\\&amp;)dl=1)\"").getMatch(0);
                // Try to find original quality link
                final String setID = br.getRegex("\"set\":\"([^<>\"]*?)\"").getMatch(0);
                final String user = getUser(this.br);
                final String ajaxpipe_token = getajaxpipeToken();
                /*
                 * If no downloadlink is there, simply try to find the fullscreen link to the picture which is located on the "theatre view"
                 * page
                 */
                if (setID != null && user != null && ajaxpipe_token != null && DLLINK == null) {
                    try {
                        logger.info("Trying to get original quality image");
                        final String fbid = getPICID(link);
                        final String data = "{\"type\":\"1\",\"fbid\":\"" + fbid + "\",\"set\":\"" + setID + "\",\"firstLoad\":true,\"ssid\":0,\"av\":\"0\"}";
                        final Browser br2 = br.cloneBrowser();
                        final String theaterView = "https://www.facebook.com/ajax/pagelet/generic.php/PhotoViewerInitPagelet?ajaxpipe=1&ajaxpipe_token=" + ajaxpipe_token + "&no_script_path=1&data=" + Encoding.urlEncode(data) + "&__user=" + user + "&__a=1&__dyn=7n8ajEyl2qm9udDgDxyF4EihUtCxO4p9GgSmEZ9LFwxBxCuUWdDx2ubhHximmey8OdUS8w&__req=jsonp_3&__rev=" + REV + "&__adt=3";
                        br2.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                        br2.getPage(theaterView);
                        DLLINK = br2.getRegex("\"url\":\"(http[^<>\"]*?" + lid + "[^<>\"]*?_o\\.jpg[^<>\"/]*?)\"").getMatch(0);
                        if (DLLINK == null) {
                            /*
                             * Failed to find original - try to get the "n" version --> Still better than the one we cannot get via generic
                             * page
                             */
                            DLLINK = br2.getRegex("\"url\":\"(http[^<>\"]*?" + lid + "[^<>\"]*?_n\\.jpg[^<>\"/]*?)\"").getMatch(0);
                        }
                    } catch (final Throwable e) {
                    }
                    if (DLLINK != null) {
                        logger.info("Found image via generic page");
                    } else {
                        logger.warning("Failed to find image via generic page");
                    }
                }
                if (DLLINK == null) {
                    DLLINK = br.getRegex("id=\"fbPhotoImage\" src=\"(https?://[^<>\"]*?)\"").getMatch(0);
                }
                if (DLLINK == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                DLLINK = DLLINK.replace("\\", "");
                DLLINK = Encoding.htmlDecode(DLLINK);

                // Try to change it to HD
                final Regex urlSplit = new Regex(DLLINK, "(https?://[a-z0-9\\-\\.]+/hphotos\\-ak\\-[a-z0-9]+)/(q\\d+/)?s\\d+x\\d+(/.+)");
                final String partOne = urlSplit.getMatch(0);
                final String partTwo = urlSplit.getMatch(2);
                if (partOne != null && partTwo != null) {
                    // Usual part
                    DLLINK = partOne + partTwo;
                }
                try {
                    con = br.openGetConnection(DLLINK);
                    if (!con.getContentType().contains("html")) {
                        link.setDownloadSize(con.getLongContentLength());
                    } else {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                    if (this.getPluginConfig().getBooleanProperty(USE_ALBUM_NAME_IN_FILENAME, false)) {
                        filename = filename + "_" + lid + ".jpg";
                    } else {
                        filename = Encoding.htmlDecode(getFileNameFromHeader(con)).trim();
                    }
                } finally {
                    try {
                        con.disconnect();
                    } catch (Throwable e) {
                    }
                }
            } else {
                filename = filename + "_" + lid + ".mp4";
            }
        }
        link.setFinalFileName(filename);
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings("deprecation")
    private String getPICID(final DownloadLink dl) {
        return new Regex(dl.getDownloadURL(), "(\\d+)$").getMatch(0);
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(account, br);
        } catch (final PluginException e) {
            account.setValid(false);
            throw e;
        }
        ai.setStatus("Valid Facebook account is active");
        ai.setUnlimitedTraffic();
        account.setValid(true);
        return ai;
    }

    @Override
    public String getAGBLink() {
        return "http://www.facebook.com/terms.php";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        if (DLLINK != null) {
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, MAXCHUNKS);
            if (dl.getConnection().getContentType().contains("html")) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        } else {
            if (accountNeeded && !this.loggedIN) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            } else {
                handleVideo(downloadLink);
            }
        }
    }

    private String getHigh() {
        return br.getRegex("%22hd_src%22%3A%22(http[^<>\"\\']*?)%22").getMatch(0);
    }

    private String getLow() {
        final String dllink = br.getRegex("%22sd_src%22%3A%22(http[^<>\"\\']*?)%22").getMatch(0);
        // if (dllink == null) dllink =
        // br.getRegex("\\\\u002522sd_src\\\\u002522\\\\u00253A\\\\u002522(http[^<>\"/]*?)\\\\u002522\\\\u00252C\\\\u002522thumbnail_src").getMatch(0);
        return dllink;
    }

    @Override
    public void handlePremium(final DownloadLink downloadLink, final Account account) throws Exception {
        requestFileInformation(downloadLink);
        if (DLLINK != null) {
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, 0);
            if (dl.getConnection().getContentType().contains("html")) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        } else {
            handleVideo(downloadLink);
        }
    }

    public void handleVideo(final DownloadLink downloadLink) throws Exception {
        if (DLLINK == null) {
            boolean preferHD = getPluginConfig().getBooleanProperty(PREFERHD);
            br.getRequest().setHtmlCode(unescape(br.toString()));
            if (preferHD) {
                DLLINK = getHigh();
                if (DLLINK == null) {
                    DLLINK = getLow();
                }
            } else {
                DLLINK = getLow();
                if (DLLINK == null) {
                    DLLINK = getHigh();
                }
            }
            if (DLLINK == null) {
                logger.warning("Final downloadlink (dllink) is null");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            DLLINK = Encoding.urlDecode(decodeUnicode(DLLINK), true);
            DLLINK = Encoding.htmlDecode(DLLINK);
            DLLINK = JSonUtils.unescape(DLLINK);
            if (DLLINK == null) {
                logger.warning("Final downloadlink (dllink) is null");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        logger.info("Final downloadlink = " + DLLINK + " starting download...");
        final String Vollkornkeks = downloadLink.getDownloadURL().replace(FACEBOOKMAINPAGE, "");
        br.setCookie(FACEBOOKMAINPAGE, "x-referer", Encoding.urlEncode(FACEBOOKMAINPAGE + Vollkornkeks + "#" + Vollkornkeks));
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private static final String LOGINFAIL_GERMAN  = "\r\nEvtl. ungültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!\r\nBedenke, dass die Facebook Anmeldung per JD nur funktioniert, wenn Facebook\r\nkeine zusätzlichen Sicherheitsabfragen beim Login deines Accounts verlangt.\r\nPrüfe das und versuchs erneut!";
    private static final String LOGINFAIL_ENGLISH = "\r\nMaybe invalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!\r\nNote that the Facebook login via JD will only work if there are no additional\r\nsecurity questions when logging in your account.\r\nCheck that and try again!";

    private void setHeaders(Browser br) {
        br.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        br.getHeaders().put("Accept-Language", "en-gb, en;q=0.9");
        br.getHeaders().put("Accept-Encoding", "gzip, deflate");
        br.getHeaders().put("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        br.setCookie("http://www.facebook.com", "locale", "en_GB");
    }

    @SuppressWarnings("unchecked")
    public void login(final Account account, Browser br) throws Exception {
        synchronized (LOCK) {
            setHeaders(br);
            // Load cookies
            br.setCookiesExclusive(true);
            final Object ret = account.getProperty("cookies", null);
            boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
            if (acmatch) {
                acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
            }
            if (acmatch && ret != null && ret instanceof HashMap<?, ?>) {
                final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                if (cookies.containsKey("c_user") && cookies.containsKey("xs") && account.isValid()) {
                    for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                        final String key = cookieEntry.getKey();
                        final String value = cookieEntry.getValue();
                        br.setCookie(FACEBOOKMAINPAGE, key, value);
                    }
                    final boolean follow = br.isFollowingRedirects();
                    try {
                        br.setFollowRedirects(true);
                        br.getPage(FACEBOOKMAINPAGE);
                    } finally {
                        br.setFollowRedirects(follow);
                    }
                    if (br.containsHTML("id=\"logoutMenu\"")) {
                        return;
                    }
                    /* Get rid of old cookies / headers */
                    br = new Browser();
                    br.setCookiesExclusive(true);
                    setHeaders(br);
                }
            }
            br.setFollowRedirects(true);
            final boolean prefer_mobile_login = false;
            // better use the website login. else the error handling below might be broken.
            if (prefer_mobile_login) {
                /* Mobile login = no crypto crap */
                br.getPage("https://m.facebook.com/");
                final Form loginForm = br.getForm(0);
                if (loginForm == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }

                }
                loginForm.remove(null);
                loginForm.put("email", Encoding.urlEncode(account.getUser()));
                loginForm.put("pass", Encoding.urlEncode(account.getPass()));
                br.submitForm(loginForm);
                br.getPage("https://www.facebook.com/");
            } else {
                br.getPage("https://www.facebook.com/login.php");
                final String lang = System.getProperty("user.language");

                final Form loginForm = br.getForm(0);
                if (loginForm == null) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }

                loginForm.remove("persistent");
                loginForm.put("persistent", "1");
                loginForm.remove(null);
                loginForm.remove("login");
                loginForm.remove("trynum");
                loginForm.remove("profile_selector_ids");
                loginForm.remove("legacy_return");
                loginForm.remove("enable_profile_selector");
                loginForm.remove("display");
                String _js_datr = br.getRegex("\"_js_datr\"\\s*,\\s*\"([^\"]+)").getMatch(0);

                br.setCookie("https://facebook.com", "_js_datr", _js_datr);
                br.setCookie("https://facebook.com", "_js_reg_fb_ref", Encoding.urlEncode("https://www.facebook.com/login.php"));
                br.setCookie("https://facebook.com", "_js_reg_fb_gate", Encoding.urlEncode("https://www.facebook.com/login.php"));
                loginForm.put("email", Encoding.urlEncode(account.getUser()));
                loginForm.put("pass", Encoding.urlEncode(account.getPass()));
                br.submitForm(loginForm);
            }

            /**
             * Facebook thinks we're an unknown device, now we prove we're not ;)
             */
            if (br.containsHTML(">Your account is temporarily locked")) {
                final String nh = br.getRegex("name=\"nh\" value=\"([a-z0-9]+)\"").getMatch(0);
                final String dstc = br.getRegex("name=\"fb_dtsg\" value=\"([^<>\"]*?)\"").getMatch(0);
                if (nh == null || dstc == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_GERMAN, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_ENGLISH, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                br.postPage(br.getURL(), "fb_dtsg=" + Encoding.urlEncode(dstc) + "&nh=" + nh + "&submit%5BContinue%5D=Continue");

                final DownloadLink dummyLink = new DownloadLink(this, "Account", "facebook.com", "http://facebook.com", true);
                String achal = br.getRegex("name=\"achal\" value=\"([a-z0-9]+)\"").getMatch(0);
                final String captchaPersistData = br.getRegex("name=\"captcha_persist_data\" value=\"([^<>\"]*?)\"").getMatch(0);
                if (captchaPersistData == null || achal == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_GERMAN, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_ENGLISH, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // Normal captcha handling
                for (int i = 1; i <= 3; i++) {
                    String captchaLink = br.getRegex("\"(https?://(www\\.)?facebook\\.com/captcha/tfbimage\\.php\\?captcha_challenge_code=[^<>\"]*?)\"").getMatch(0);
                    if (captchaLink == null) {
                        break;
                    }
                    captchaLink = Encoding.htmlDecode(captchaLink);

                    String code;
                    try {
                        code = getCaptchaCode(captchaLink, dummyLink);
                    } catch (final Exception e) {
                        continue;
                    }
                    br.postPage(br.getURL(), "fb_dtsg=" + Encoding.urlEncode(dstc) + "&nh=" + nh + "&geo=true&captcha_persist_data=" + Encoding.urlEncode(captchaPersistData) + "&captcha_response=" + Encoding.urlEncode(code) + "&achal=" + achal + "&submit%5BSubmit%5D=Submit");
                }

                // reCaptcha handling
                for (int i = 1; i <= 3; i++) {
                    final String rcID = br.getRegex("\"recaptchaPublicKey\":\"([^<>\"]*?)\"").getMatch(0);
                    if (rcID == null) {
                        break;
                    }

                    final String extraChallengeParams = br.getRegex("name=\"extra_challenge_params\" value=\"([^<>\"]*?)\"").getMatch(0);
                    final String captchaSession = br.getRegex("name=\"captcha_session\" value=\"([^<>\"]*?)\"").getMatch(0);
                    if (extraChallengeParams == null || captchaSession == null) {
                        break;
                    }

                    final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                    final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
                    rc.setId(rcID);
                    rc.load();
                    final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                    String c;
                    try {
                        c = getCaptchaCode("recaptcha", cf, dummyLink);
                    } catch (final Exception e) {
                        continue;
                    }
                    br.postPage(br.getURL(), "fb_dtsg=" + Encoding.urlEncode(dstc) + "&nh=" + nh + "&geo=true&captcha_persist_data=" + Encoding.urlEncode(captchaPersistData) + "&captcha_session=" + Encoding.urlEncode(captchaSession) + "&extra_challenge_params=" + Encoding.urlEncode(extraChallengeParams) + "&recaptcha_type=password&recaptcha_challenge_field=" + Encoding.urlEncode(rc.getChallenge()) + "&captcha_response=" + Encoding.urlEncode(c) + "&achal=1&submit%5BSubmit%5D=Submit");
                }

                for (int i = 1; i <= 3; i++) {
                    if (br.containsHTML(">To confirm your identity, please enter your birthday")) {
                        achal = br.getRegex("name=\"achal\" value=\"([a-z0-9]+)\"").getMatch(0);
                        if (achal == null) {
                            break;
                        }
                        String birthdayVerificationAnswer;
                        try {
                            birthdayVerificationAnswer = getUserInput("Enter your birthday (dd:MM:yyyy)", dummyLink);
                        } catch (final Exception e) {
                            continue;
                        }
                        final String[] bdSplit = birthdayVerificationAnswer.split(":");
                        if (bdSplit == null || bdSplit.length != 3) {
                            continue;
                        }
                        int bdDay = 0, bdMonth = 0, bdYear = 0;
                        try {
                            bdDay = Integer.parseInt(bdSplit[0]);
                            bdMonth = Integer.parseInt(bdSplit[1]);
                            bdYear = Integer.parseInt(bdSplit[2]);
                        } catch (final Exception e) {
                            continue;
                        }
                        br.postPage(br.getURL(), "fb_dtsg=" + Encoding.urlEncode(dstc) + "&nh=" + nh + "&geo=true&birthday_captcha_month=" + bdMonth + "&birthday_captcha_day=" + bdDay + "&birthday_captcha_year=" + bdYear + "&captcha_persist_data=" + Encoding.urlEncode(captchaPersistData) + "&achal=" + achal + "&submit%5BSubmit%5D=Submit");
                    } else {
                        break;
                    }
                }
            } else if (br.containsHTML("/checkpoint/")) {
                br.getPage("https://www.facebook.com/checkpoint/");
                final String postFormID = br.getRegex("name=\"post_form_id\" value=\"(.*?)\"").getMatch(0);
                final String nh = br.getRegex("name=\"nh\" value=\"(.*?)\"").getMatch(0);
                if (nh == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_GERMAN, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_ENGLISH, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                br.postPage("https://www.facebook.com/checkpoint/", "post_form_id=" + postFormID + "&lsd=GT_Up&submit%5BContinue%5D=Weiter&nh=" + nh);
                br.postPage("https://www.facebook.com/checkpoint/", "post_form_id=" + postFormID + "&lsd=GT_Up&submit%5BThis+is+Okay%5D=Das+ist+OK&nh=" + nh);
                br.postPage("https://www.facebook.com/checkpoint/", "post_form_id=" + postFormID + "&lsd=GT_Up&machine_name=&submit%5BDon%27t+Save%5D=Nicht+speichern&nh=" + nh);
                br.postPage("https://www.facebook.com/checkpoint/", "post_form_id=" + postFormID + "&lsd=GT_Up&machine_name=&submit%5BDon%27t+Save%5D=Nicht+speichern&nh=" + nh);
            }
            if (br.getCookie(FACEBOOKMAINPAGE, "c_user") == null || br.getCookie(FACEBOOKMAINPAGE, "xs") == null) {
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_GERMAN, PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_ENGLISH, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            // Save cookies
            final HashMap<String, String> cookies = new HashMap<String, String>();
            final Cookies add = br.getCookies(FACEBOOKMAINPAGE);
            for (final Cookie c : add.getCookies()) {
                cookies.put(c.getKey(), c.getValue());
            }
            account.setProperty("name", Encoding.urlEncode(account.getUser()));
            account.setProperty("pass", Encoding.urlEncode(account.getPass()));
            account.setProperty("cookies", cookies);
            account.setValid(true);
            synchronized (LOCK) {
                checkFeatureDialog();
            }
        }
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
    }

    @Override
    public String getDescription() {
        return "JDownloader's Facebook Plugin helps downloading videoclips and photo galleries. Facebook provides two different video qualities.";
    }

    public static final String  FASTLINKCHECK_PICTURES         = "FASTLINKCHECK_PICTURES";
    public static final boolean FASTLINKCHECK_PICTURES_DEFAULT = true;
    private static final String USE_ALBUM_NAME_IN_FILENAME     = "USE_ALBUM_NAME_IN_FILENAME";

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), PREFERHD, JDL.L("plugins.hoster.facebookcomvideos.preferhd", "Videos: Prefer HD quality")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), FASTLINKCHECK_PICTURES, JDL.L("plugins.hoster.facebookcomvideos.fastlinkcheckpictures", "Photos: Enable fast linkcheck (filesize won't be shown in linkgrabber)?")).setDefaultValue(FASTLINKCHECK_PICTURES_DEFAULT));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), USE_ALBUM_NAME_IN_FILENAME, JDL.L("plugins.hoster.facebookcomvideos.usealbumnameinfilename", "Photos: Use album name in filename [note that filenames change once the download starts]?")).setDefaultValue(true));
    }

    private String unescape(final String s) {
        /* we have to make sure the youtube plugin is loaded */
        if (pluginloaded == false) {
            final PluginForHost plugin = JDUtilities.getPluginForHost("youtube.com");
            if (plugin == null) {
                throw new IllegalStateException("youtube plugin not found!");
            }
            pluginloaded = true;
        }
        return jd.plugins.hoster.Youtube.unescape(s);
    }

    public String decodeUnicode(final String s) {
        final Pattern p = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        String res = s;
        final Matcher m = p.matcher(res);
        while (m.find()) {
            res = res.replaceAll("\\" + m.group(0), Character.toString((char) Integer.parseInt(m.group(1), 16)));
        }
        return res;
    }

    private static String getUser(final Browser br) {
        return jd.plugins.decrypter.FaceBookComGallery.getUser(br);
    }

    private String getajaxpipeToken() {
        return br.getRegex("\"ajaxpipe_token\":\"([^<>\"]*?)\"").getMatch(0);
    }

    private void checkFeatureDialog() {
        SubConfiguration config = null;
        try {
            config = getPluginConfig();
            if (config.getBooleanProperty("featuredialog_Shown", Boolean.FALSE) == false) {
                if (config.getProperty("featuredialog_Shown2") == null) {
                    showFeatureDialogAll();
                } else {
                    config = null;
                }
            } else {
                config = null;
            }
        } catch (final Throwable e) {
        } finally {
            if (config != null) {
                config.setProperty("featuredialog_Shown", Boolean.TRUE);
                config.setProperty("featuredialog_Shown2", "shown");
                config.save();
            }
        }
    }

    private static void showFeatureDialogAll() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    try {
                        String message = "";
                        String title = null;
                        title = "Facebook.com Plugin";
                        final String lang = System.getProperty("user.language");
                        if ("de".equalsIgnoreCase(lang)) {
                            message += "Du benutzt deinen Facebook Account zum ersten mal in JDownloader.\r\n";
                            message += "Da JDownloader keine Facebook App ist loggt er sich genau wie du per Browser ein.\r\n";
                            message += "Es gibt also keinen Austausch (privater) Facebook Daten mit JD.\r\n";
                            message += "Wir wahren deine Privatsphäre!";
                        } else {
                            message += "You're using your Facebook account in JDownloader for the first time.\r\n";
                            message += "Because JDownloader is not a Facebook App it logs in Facebook just like you via browser.\r\n";
                            message += "There is no (private) data exchange between JD and Facebook!\r\n";
                            message += "We respect your privacy!";
                        }
                        JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.PLAIN_MESSAGE, JOptionPane.INFORMATION_MESSAGE, null);
                    } catch (Throwable e) {
                    }
                }
            });
        } catch (Throwable e) {
        }
    }

    @Override
    public void reset() {
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
        if (Boolean.TRUE.equals(acc.getBooleanProperty("free"))) {
            /* free accounts also have captchas */
            return true;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("nopremium"))) {
            /* free accounts also have captchas */
            return true;
        }
        if (acc.getStringProperty("session_type") != null && !"premium".equalsIgnoreCase(acc.getStringProperty("session_type"))) {
            return true;
        }
        return false;
    }
}