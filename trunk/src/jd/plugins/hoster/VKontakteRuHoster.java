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

import java.io.IOException;
import java.net.ConnectException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.controlling.linkcrawler.CrawledLink;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
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
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

//Links are coming from a decrypter
@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "vkontakte.ru" }, urls = { "http://vkontaktedecrypted\\.ru/(picturelink/(?:\\-)?\\d+_\\d+(\\?tag=[\\d\\-]+)?|audiolink/[\\d\\-]+_\\d+|videolink/[\\d\\-]+)|https?://vk\\.com/doc[\\d\\-]+_[\\d\\-]+(\\?hash=[a-z0-9]+)?|https?://(?:c|p)s[a-z0-9\\-]+\\.(?:vk\\.com|userapi\\.com|vk\\.me)/[^<>\"]+\\.mp3" }, flags = { 2 })
public class VKontakteRuHoster extends PluginForHost {

    private static final String DOMAIN                                = "http://vk.com";
    private static final String TYPE_AUDIOLINK                        = "http://vkontaktedecrypted\\.ru/audiolink/(?:\\-)?\\d+_\\d+";
    private static final String TYPE_VIDEOLINK                        = "http://vkontaktedecrypted\\.ru/videolink/[\\d\\-]+";
    private static final String TYPE_AUDIO_DIRECT                     = "https?://(?:c|p)s[a-z0-9\\-]+\\.(?:vk\\.com|userapi\\.com|vk\\.me)/[^<>\"]+\\.mp3";
    private static final String TYPE_PICTURELINK                      = "http://vkontaktedecrypted\\.ru/picturelink/(\\-)?[\\d\\-]+_[\\d\\-]+(\\?tag=[\\d\\-]+)?";
    private static final String TYPE_DOCLINK                          = "https?://vk\\.com/doc[\\d\\-]+_\\d+(\\?hash=[a-z0-9]+)?";
    private int                 MAXCHUNKS                             = 1;
    private static final String TEMPORARILYBLOCKED                    = jd.plugins.decrypter.VKontakteRu.TEMPORARILYBLOCKED;
    /* Settings stuff */
    private static final String USECOOKIELOGIN                        = "USECOOKIELOGIN";
    private static final String FASTLINKCHECK_VIDEO                   = "FASTLINKCHECK_VIDEO";
    private static final String FASTLINKCHECK_PICTURES                = "FASTLINKCHECK_PICTURES";
    private static final String FASTLINKCHECK_AUDIO                   = "FASTLINKCHECK_AUDIO";
    private static final String ALLOW_BEST                            = "ALLOW_BEST";
    private static final String ALLOW_240P                            = "ALLOW_240P";
    private static final String ALLOW_360P                            = "ALLOW_360P";
    private static final String ALLOW_480P                            = "ALLOW_480P";
    private static final String ALLOW_720P                            = "ALLOW_720P";
    private static final String VKWALL_GRAB_ALBUMS                    = "VKWALL_GRAB_ALBUMS";
    private static final String VKWALL_GRAB_PHOTOS                    = "VKWALL_GRAB_PHOTOS";
    private static final String VKWALL_GRAB_AUDIO                     = "VKWALL_GRAB_AUDIO";
    private static final String VKWALL_GRAB_VIDEO                     = "VKWALL_GRAB_VIDEO";
    private static final String VKWALL_GRAB_LINK                      = "VKWALL_GRAB_LINK";
    public static final String  VKWALL_GRAB_DOCS                      = "VKWALL_GRAB_DOCS";
    private static final String VKVIDEO_USEIDASPACKAGENAME            = "VKVIDEO_USEIDASPACKAGENAME";
    private static final String VKAUDIOS_USEIDASPACKAGENAME           = "VKAUDIOS_USEIDASPACKAGENAME";
    private static final String VKPHOTO_CORRECT_FINAL_LINKS           = "VKPHOTO_CORRECT_FINAL_LINKS";
    public static final String  VKADVANCED_USER_AGENT                 = "VKADVANCED_USER_AGENT";

    /* html patterns */
    public static final String  HTML_VIDEO_NO_ACCESS                  = "NO_ACCESS";
    public static final String  HTML_VIDEO_REMOVED_FROM_PUBLIC_ACCESS = "This video has been removed from public access";

    private final boolean       docs_add_unique_id                    = true;

    private static Object       LOCK                                  = new Object();
    private String              finalUrl                              = null;

    private String              ownerID                               = null;
    private String              contentID                             = null;
    private String              mainlink                              = null;

    public VKontakteRuHoster(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium();
        this.setConfigElements();
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */

    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
    }

    public static String URLDecode(String urlCoded) throws IOException {
        final String cp1251 = URLDecoder.decode(urlCoded, "cp1251");
        final String utf8 = URLDecoder.decode(urlCoded, "UTF-8");
        int cp1251Count = 0;
        int utf8Count = 0;
        for (int index = 0; index < cp1251.length(); index++) {
            if ('\uFFFD' == cp1251.charAt(index)) {
                cp1251Count++;
            }
        }
        for (int index = 0; index < utf8.length(); index++) {
            if ('\uFFFD' == utf8.charAt(index)) {
                utf8Count++;
            }
        }
        if (cp1251Count < utf8Count) {
            return cp1251;
        } else {
            return utf8;
        }
    }

    @Override
    public CrawledLink convert(DownloadLink link) {
        final CrawledLink ret = super.convert(link);
        final String url = link.getDownloadURL();
        if (url != null && url.matches(TYPE_AUDIO_DIRECT)) {
            final String filename = new Regex(url, "/([^<>\"/]+\\.mp3)$").getMatch(0);
            if (filename != null) {
                try {
                    final String urlDecoded = URLDecode(filename);
                    link.setFinalFileName(urlDecoded);
                } catch (final Throwable e) {
                    link.setName(filename);
                }
            }
        }
        return ret;
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        setConstants(link);
        int checkstatus = 0;
        String filename = null;
        /* Check if offline was set via decrypter */
        if (link.getBooleanProperty("offline", false)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }

        this.finalUrl = null;
        this.setBrowserExclusive();

        this.br.setFollowRedirects(false);
        if (link.getDownloadURL().matches(TYPE_AUDIO_DIRECT)) {
            this.finalUrl = link.getDownloadURL();
            this.br.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            /* Prefer filename inside url */
            filename = new Regex(this.finalUrl, "/([^<>\"/]+\\.mp3)$").getMatch(0);
            try {
                try {
                    /* They do not accept HEAD connections */
                    con = br.openGetConnection(link.getDownloadURL());
                } catch (final BrowserException e) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                if (!con.getContentType().contains("html")) {
                    if (filename == null) {
                        filename = getFileNameFromHeader(con);
                    }
                    if (filename != null) {
                        try {
                            final String urlDecoded = URLDecode(filename);
                            link.setFinalFileName(urlDecoded);
                        } catch (final Throwable e) {
                            link.setName(filename);
                        }
                    }
                    link.setDownloadSize(con.getLongContentLength());
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        } else if (link.getDownloadURL().matches(VKontakteRuHoster.TYPE_DOCLINK)) {
            if (link.getLinkID() == null || !link.getLinkID().matches("")) {
                link.setLinkID(new Regex(link.getDownloadURL(), "/doc((?:\\-)?\\d+_\\d+)").getMatch(0));
            }
            this.MAXCHUNKS = 0;
            this.br.getPage(link.getDownloadURL());
            if (this.br.containsHTML("File deleted")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (this.br.containsHTML("This document is available only to its owner\\.")) {
                link.getLinkStatus().setStatusText("This document is available only to its owner");
                link.setName(new Regex(link.getDownloadURL(), "([a-z0-9]+)$").getMatch(0));
                return AvailableStatus.TRUE;
            }
            filename = this.br.getRegex("title>([^<>\"]*?)</title>").getMatch(0);
            this.finalUrl = this.br.getRegex("var src = \\'(https?://[^<>\"]*?)\\';").getMatch(0);
            if (filename == null || this.finalUrl == null) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            /* Sometimes filenames on site are cut - finallink usually contains the full filenames */
            final String betterFilename = new Regex(this.finalUrl, "docs/[a-z0-9]+/([^<>\"]*?)\\?extra=.+").getMatch(0);
            if (betterFilename != null) {
                filename = Encoding.htmlDecode(betterFilename).trim();
            } else {
                filename = Encoding.htmlDecode(filename.trim());
            }
            if (docs_add_unique_id) {
                filename = link.getLinkID() + filename;
            }
            checkstatus = this.linkOk(link, filename);
            if (checkstatus != 1) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        } else {
            // Check if login is required to check/download
            final boolean noLogin = checkNoLoginNeeded(link);
            final Account aa = AccountController.getInstance().getValidAccount(this);
            if (!noLogin && aa == null) {
                link.getLinkStatus().setStatusText("Only downlodable via account!");
                return AvailableStatus.UNCHECKABLE;
            } else if (aa != null) {
                /* Always login if possible. */
                this.login(this.br, aa, false);
            }
            if (link.getDownloadURL().matches(VKontakteRuHoster.TYPE_AUDIOLINK)) {
                String finalFilename = link.getFinalFileName();
                if (finalFilename == null) {
                    finalFilename = link.getName();
                }
                this.finalUrl = link.getStringProperty("directlink", null);
                checkstatus = this.linkOk(link, finalFilename);
                if (checkstatus != 1) {
                    String url = null;
                    this.br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                    final String postID = link.getStringProperty("postID", null);
                    final String fromId = link.getStringProperty("fromId", null);
                    if (postID != null && fromId != null) {
                        logger.info("Trying to refresh audiolink directlink via wall-handling");
                        /* We got the info we need to access our single mp3 relatively directly as it initially came from a 'wall'. */
                        final String post = "act=get_wall_playlist&al=1&local_id=" + postID + "&oid=" + fromId + "&wall_type=own";
                        br.postPage("https://vk.com/audio", post);
                        url = br.getRegex("\"0\"\\:\"" + Pattern.quote(this.ownerID) + "\"\\,\"1\"\\:\"" + Pattern.quote(this.contentID) + "\"\\,\"2\"\\:(\"[^\"]+\")").getMatch(0);
                        if (url == null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        /* Decodes the json string */
                        url = (String) DummyScriptEnginePlugin.jsonToJavaObject(url);
                    } else {
                        logger.info("refreshing audiolink directlink via album-handling");
                        /*
                         * No way to easily get the needed info directly --> Load the complete audio album and find a fresh directlink for
                         * our ID.
                         *
                         * E.g. get-play-link: https://vk.com/audio?id=<ownerID>&audio_id=<contentID>
                         */
                        this.postPageSafe(aa, link, "https://vk.com/audio", getAudioAlbumPostString(this.mainlink, this.ownerID));
                        final String[] audioData = getAudioDataArray(this.br);
                        for (final String singleAudioData : audioData) {
                            final String[] singleAudioDataAsArray = new Regex(singleAudioData, "\\'(.*?)\\'").getColumn(0);
                            final String content_id = singleAudioDataAsArray[1];
                            final String directlink = singleAudioDataAsArray[2];
                            if (content_id == null || directlink == null) {
                                logger.warning("FATAL error in audiolink refresh directlink handling");
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }
                            if (content_id.equals(this.contentID)) {
                                url = Encoding.htmlDecode(directlink);
                                break;
                            }
                        }
                    }
                    if (url == null) {
                        logger.warning("Failed to refresh audiolink directlink");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    this.finalUrl = url;
                    checkstatus = this.linkOk(link, finalFilename);
                    if (checkstatus != 1) {
                        logger.info("Refreshed audiolink directlink seems not to work --> Link is probably offline");
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                    link.setProperty("directlink", this.finalUrl);
                }
            } else if (link.getDownloadURL().matches(VKontakteRuHoster.TYPE_VIDEOLINK)) {
                this.MAXCHUNKS = 0;
                this.br.setFollowRedirects(true);
                this.finalUrl = link.getStringProperty("directlink", null);
                // Check if directlink is expired
                checkstatus = this.linkOk(link, link.getFinalFileName());
                if (checkstatus != 1) {
                    final String oid = link.getStringProperty("userid", null);
                    final String id = link.getStringProperty("videoid", null);
                    this.br.getPage("http://vk.com/video.php?act=a_flash_vars&vid=" + oid + "_" + id);
                    if (br.containsHTML(VKontakteRuHoster.HTML_VIDEO_NO_ACCESS) || br.containsHTML(VKontakteRuHoster.HTML_VIDEO_REMOVED_FROM_PUBLIC_ACCESS)) {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                    final LinkedHashMap<String, String> availableQualities = this.findAvailableVideoQualities();
                    if (availableQualities == null) {
                        this.logger.info("vk.com: Couldn't find any available qualities for videolink");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    this.finalUrl = availableQualities.get(link.getStringProperty("selectedquality", null));
                    if (this.finalUrl == null) {
                        this.logger.warning("Could not find new link for selected quality...");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
                checkstatus = this.linkOk(link, link.getStringProperty("directfilename", null));
                if (checkstatus != 1) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } else {
                this.finalUrl = link.getStringProperty("picturedirectlink", null);
                if (this.finalUrl == null) {
                    final String photo_list_id = link.getStringProperty("photo_list_id", null);
                    final String module = link.getStringProperty("photo_module", null);
                    final String photoID = getPhotoID(link);
                    if (module != null && photo_list_id != null) {
                        /* Access photo inside wall-post */
                        this.br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                        this.br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                        this.postPageSafe(aa, link, "http://vk.com/al_photos.php", "act=show&al=1&list=" + photo_list_id + "&module=" + module + "&photo=" + photoID);
                    } else {
                        /* Access normal photo / photo inside album */
                        String albumID = link.getStringProperty("albumid");
                        if (albumID == null) {
                            this.getPageSafe(aa, link, "http://vk.com/photo" + photoID);
                            if (this.br.containsHTML("Unknown error|Unbekannter Fehler|Access denied")) {
                                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                            }
                            albumID = this.br.getRegex("class=\"active_link\">[\t\n\r ]+<a href=\"/(.*?)\"").getMatch(0);
                            if (albumID == null) {
                                this.logger.info("vk.com: albumID is null");
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }
                            link.setProperty("albumid", albumID);
                        }
                        this.br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                        this.br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                        this.postPageSafe(aa, link, "http://vk.com/al_photos.php", "act=show&al=1&module=photos&list=" + albumID + "&photo=" + photoID);
                    }
                    if (this.br.containsHTML(">Unfortunately, this photo has been deleted") || this.br.containsHTML(">Access denied<")) {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings("deprecation")
    public void doFree(final DownloadLink downloadLink) throws Exception, PluginException {
        if (downloadLink.getDownloadURL().matches(VKontakteRuHoster.TYPE_DOCLINK)) {
            if (this.br.containsHTML("This document is available only to its owner\\.")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "This document is available only to its owner");
            }
        } else if (downloadLink.getDownloadURL().matches(TYPE_PICTURELINK)) {
            if (this.finalUrl == null) {
                /*
                 * Because of the availableCheck, we already know that the picture is online but we can't be sure that it really is
                 * downloadable!
                 */
                getHighestQualityPic(downloadLink);
                downloadLink.setProperty("picturedirectlink", this.finalUrl);
            }
        }
        br.getHeaders().put("Accept-Encoding", "identity");
        this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, this.finalUrl, true, this.MAXCHUNKS);
        handleServerErrors(downloadLink);
        this.dl.startDownload();
    }

    @SuppressWarnings("deprecation")
    private void handleServerErrors(final DownloadLink downloadLink) throws PluginException, IOException {
        final URLConnectionAdapter con = this.dl.getConnection();
        if (con.getResponseCode() == 416) {
            con.disconnect();
            this.logger.info("Resume failed --> Retrying from zero");
            downloadLink.setChunksProgress(null);
            throw new PluginException(LinkStatus.ERROR_RETRY);
        }
        if (con.getContentType().contains("html")) {
            this.logger.info("vk.com: Plugin broken after download-try");
            this.br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            if (this.getPluginConfig().getBooleanProperty(this.USECOOKIELOGIN, false)) {
                this.logger.info("Logging in with cookies.");
                this.login(this.br, account, false);
                this.logger.info("Logged in successfully with cookies...");
            } else {
                this.logger.info("Logging in without cookies (forced login)...");
                this.login(this.br, account, true);
                this.logger.info("Logged in successfully without cookies (forced login)!");
            }
        } catch (final PluginException e) {
            this.logger.info("Login failed!");
            account.setValid(false);
            throw e;
        }
        ai.setUnlimitedTraffic();
        ai.setStatus("Registered (free) User");
        return ai;
    }

    /* Same function in hoster and decrypterplugin, sync it!! */
    private LinkedHashMap<String, String> findAvailableVideoQualities() {
        /* Find needed information */
        this.br.getRequest().setHtmlCode(this.br.toString().replace("\\", ""));
        final String[][] qualities = { { "url720", "720p" }, { "url480", "480p" }, { "url360", "360p" }, { "url240", "240p" } };
        final LinkedHashMap<String, String> foundQualities = new LinkedHashMap<String, String>();
        for (final String[] qualityInfo : qualities) {
            final String finallink = this.getJson(qualityInfo[0]);
            if (finallink != null) {
                foundQualities.put(qualityInfo[1], finallink);
            }
        }
        return foundQualities;
    }

    private void generalErrorhandling() throws PluginException {
        if (this.br.containsHTML(VKontakteRuHoster.TEMPORARILYBLOCKED)) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Too many requests in a short time", 60 * 1000l);
        }
    }

    @Override
    public String getAGBLink() {
        return "http://vk.com/help.php?page=terms";
    }

    private String getJson(final String key) {
        return getJson(this.br.toString(), key);
    }

    private String getJson(final String source, final String parameter) {
        String result = new Regex(source, "\"" + parameter + "\":([\t\n\r ]+)?([0-9\\.\\-]+)").getMatch(1);
        if (result == null) {
            result = new Regex(source, "\"" + parameter + "\":([\t\n\r ]+)?\"([^<>\"]*?)\"").getMatch(1);
        }
        return result;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    /* Handle all kinds of stuff that disturbs the downloadflow */
    private void getPageSafe(final Account acc, final DownloadLink dl, final String page) throws Exception {
        this.br.getPage(page);
        if (acc != null && this.br.getRedirectLocation() != null && this.br.getRedirectLocation().contains("login.vk.com/?role=fast")) {
            this.logger.info("Avoiding 'https://login.vk.com/?role=fast&_origin=' security check by re-logging in...");
            // Force login
            this.login(this.br, acc, true);
            this.br.getPage(page);
        } else if (acc != null && this.br.toString().length() < 100 && this.br.toString().trim().matches("\\d+<\\!><\\!>\\d+<\\!>\\d+<\\!>\\d+<\\!>[a-z0-9]+")) {
            this.logger.info("Avoiding possible outdated cookie/invalid account problem by re-logging in...");
            // Force login
            this.login(this.br, acc, true);
            this.br.getPage(page);
        }
        this.generalErrorhandling();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        /* Doc-links and other links with permission can be downloaded without login */
        if (downloadLink.getDownloadURL().matches(VKontakteRuHoster.TYPE_DOCLINK) || downloadLink.getDownloadURL().matches(VKontakteRuHoster.TYPE_AUDIO_DIRECT)) {
            this.requestFileInformation(downloadLink);
            this.doFree(downloadLink);
        } else if (checkNoLoginNeeded(downloadLink)) {
            this.requestFileInformation(downloadLink);
            this.doFree(downloadLink);
        } else {
            try {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            } catch (final Throwable e) {
                if (e instanceof PluginException) {
                    throw (PluginException) e;
                }
            }
            throw new PluginException(LinkStatus.ERROR_FATAL, "Download only possible with account!");
        }
    }

    private boolean checkNoLoginNeeded(final DownloadLink dl) {
        boolean noLogin = dl.getBooleanProperty("nologin", false);
        if (!noLogin) {
            noLogin = dl.getDownloadURL().matches(TYPE_PICTURELINK);
        }
        return noLogin;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        this.requestFileInformation(link);
        this.login(this.br, account, false);
        this.doFree(link);
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */

    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    /**
     * Checks a given directlink for content. Sets finalfilename as final filename if finalfilename != null - else sets server filename as
     * final filename.
     *
     * @return <b>1</b>: Link is valid and can be downloaded, <b>0</b>: Link leads to HTML, times out or other problems occured, <b>404</b>:
     *         Server 404 response
     */
    private int linkOk(final DownloadLink downloadLink, final String finalfilename) throws IOException {
        final Browser br2 = this.br.cloneBrowser();
        /* In case the link redirects to the finallink */
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            br2.getHeaders().put("Accept-Encoding", "identity");
            try {
                con = br2.openGetConnection(this.finalUrl);
            } catch (final BrowserException ebr) {
                /* This happens e.g. for temporarily unavailable videos. */
                throw ebr;
            } catch (final Throwable e) {
                return 0;
            }
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
                if (finalfilename == null) {
                    downloadLink.setFinalFileName(Encoding.htmlDecode(Plugin.getFileNameFromHeader(con)));
                } else {
                    downloadLink.setFinalFileName(finalfilename);
                }
            } else {
                if (con.getResponseCode() == 404) {
                    return 404;
                }
                return 0;
            }
            return 1;
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
    }

    /**
     * Checks a given photo directlink for content. Sets finalfilename as final filename if finalfilename != null - else sets server
     * filename as final filename.
     *
     * @return <b>true</b>: Link is valid and can be downloaded <b>false</b>: Link leads to HTML, times out or other problems occured - link
     *         is not downloadable!
     */
    private boolean photolinkOk(final DownloadLink downloadLink, final String finalfilename) throws IOException {
        final Browser br2 = this.br.cloneBrowser();
        /* In case the link redirects to the finallink */
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        /* Correct final URLs according to users' plugin settings. */
        photo_correctLink();

        /* Ignore invalid urls. Usually if we have such an url the picture is serverside temporarily unavailable. */
        if (this.finalUrl.contains("_null_")) {
            return false;
        }
        try {
            br2.getHeaders().put("Accept-Encoding", "identity");
            try {
                con = br2.openGetConnection(this.finalUrl);
            } catch (final BrowserException ebr) {
                logger.info("BrowserException on directlink: " + this.finalUrl);
                return false;
            } catch (final ConnectException ec) {
                logger.info("Directlink timed out: " + this.finalUrl);
                return false;
            } catch (final Throwable e) {
                return false;
            }
            if (con.getLongContentLength() <= 100 || con.getResponseCode() == 404 || con.getResponseCode() == 502) {
                /* Photo is supposed to be online but it's not downloadable */
                return false;
            }
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
                if (finalfilename == null) {
                    downloadLink.setFinalFileName(Encoding.htmlDecode(getFileNameFromHeader(con)));
                } else {
                    downloadLink.setFinalFileName(finalfilename);
                }
                return true;
            } else {
                return false;
            }
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
    }

    /** TODO: Maybe add login via API: https://vk.com/dev/auth_mobile */
    @SuppressWarnings("unchecked")
    public void login(final Browser br, final Account account, final boolean force) throws Exception {
        synchronized (VKontakteRuHoster.LOCK) {
            try {
                /* Load cookies */
                br.setCookiesExclusive(true);
                prepBrowser(br);
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
                            br.setCookie(VKontakteRuHoster.DOMAIN, key, value);
                        }
                        return;
                    }
                }
                br.clearCookies("http://vk.com/login.php");
                br.setFollowRedirects(true);
                br.getPage("http://vk.com/login.php");
                final String damnlg_h = br.getRegex("name=\"lg_h\" value=\"([^<>\"]*?)\"").getMatch(0);
                String damnIPH = br.getRegex("name=\"ip_h\" value=\"(.*?)\"").getMatch(0);
                if (damnIPH == null) {
                    damnIPH = br.getRegex("\\{loginscheme: \\'https\\', ip_h: \\'(.*?)\\'\\}").getMatch(0);
                }
                if (damnIPH == null) {
                    damnIPH = br.getRegex("loginscheme: \\'https\\'.*?ip_h: \\'(.*?)\\'").getMatch(0);
                }
                if (damnIPH == null || damnlg_h == null) {
                    this.logger.info("one or more login values are missing --> Login broken");
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłąd wtyczki, skontaktuj się z Supportem JDownloadera!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                br.postPage("http://vk.com/login.php", "op=a_login_attempt&login=" + Encoding.urlEncode(account.getUser()));
                br.postPage("https://login.vk.com/", "act=login&to=&ip_h=" + damnIPH + "&lg_h=" + damnlg_h + "&email=" + Encoding.urlEncode(account.getUser()) + "&pass=" + Encoding.urlEncode(account.getPass()) + "&expire=");
                /* Do NOT check based on cookies as they sometimes change them! */
                if (!br.containsHTML("id=\"logout_link\"")) {
                    this.logger.info("login failed --> Account invalid ?!");
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername/Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                /* Finish login if needed */
                final Form lol = br.getFormbyProperty("name", "login");
                if (lol != null) {
                    lol.put("email", Encoding.urlEncode(account.getUser()));
                    lol.put("pass", Encoding.urlEncode(account.getPass()));
                    lol.put("expire", "0");
                    br.submitForm(lol);
                }
                /* Save cookies */
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = br.getCookies(VKontakteRuHoster.DOMAIN);
                for (final Cookie c : add.getCookies()) {
                    if ("deleted".equalsIgnoreCase(c.getValue())) {
                        continue;
                    }
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

    private void postPageSafe(final Account acc, final DownloadLink dl, final String page, final String postData) throws Exception {
        this.br.postPage(page, postData);
        if (acc != null && this.br.getRedirectLocation() != null && this.br.getRedirectLocation().contains("login.vk.com/?role=fast")) {
            this.logger.info("Avoiding 'https://login.vk.com/?role=fast&_origin=' security check by re-logging in...");
            // Force login
            this.login(this.br, acc, true);
            this.br.postPage(page, postData);
        } else if (acc != null && this.br.toString().length() < 100 && this.br.toString().trim().matches("\\d+<\\!><\\!>\\d+<\\!>\\d+<\\!>\\d+<\\!>[a-z0-9]+")) {
            this.logger.info("Avoiding possible outdated cookie/invalid account problem by re-logging in...");
            // Force login
            this.login(this.br, acc, true);
            this.br.postPage(page, postData);
        }
        this.generalErrorhandling();
    }

    @SuppressWarnings("deprecation")
    public static void prepBrowser(final Browser br) {
        String useragent = SubConfiguration.getConfig("vkontakte.ru").getStringProperty(VKADVANCED_USER_AGENT, default_user_agent);
        if (useragent.equals("") || useragent.length() <= 3) {
            useragent = default_user_agent;
        }
        br.getHeaders().put("User-Agent", useragent);
        /* Set english language */
        br.setCookie("http://vk.com/", "remixlang", "3");
        br.setReadTimeout(1 * 60 * 1000);
        br.setConnectTimeout(2 * 60 * 1000);
        /* Loads can be very high. Site sometimes returns more than 10 000 entries with 1 request. */
        br.setLoadLimit(br.getLoadLimit() * 4);
    }

    /**
     * Try to get best quality and test links until a working link is found. will also handle errors in case
     *
     * @throws IOException
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void getHighestQualityPic(final DownloadLink dl) throws Exception {
        final String json = br.getRegex("<\\!json>(.*?)<\\!><\\!json>").getMatch(0);
        if (json == null) {
            logger.warning("Failed to find source json of picturelink");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        Map<String, Object> sourcemap = null;
        final String thisid = getPhotoID(dl);
        ArrayList<String> entries = (ArrayList) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(json);
        for (final Object entry : entries) {
            if (entry instanceof Map) {
                Map<String, Object> entrymap = (Map<String, Object>) entry;
                final String entry_id = (String) entrymap.get("id");
                if (entry_id.equals(thisid)) {
                    sourcemap = entrymap;
                    break;
                }

            }
        }
        if (sourcemap == null) {
            logger.warning("Failed to find specified source json of picturelink");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        boolean success = false;
        /* Count how many possible downloadlinks we have */
        int links_count = 0;
        final String[] qs = { "w_", "z_", "y_", "x_", "m_" };
        for (final String q : qs) {
            try {
                if (this.isAbort()) {
                    logger.info("User stopped downloads --> Stepping out of getHighestQualityPic to avoid 'freeze' of the current DownloadLink");
                    /* Avoid unnecessary 'plugin defect's in the logs. */
                    throw new PluginException(LinkStatus.ERROR_RETRY, "User aborted download");
                }
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
            }
            final String srcstring = q + "src";
            final Object picobject = sourcemap.get(srcstring);
            /* Check if the link we eventually found is downloadable. */
            if (picobject != null) {
                this.finalUrl = (String) picobject;
                links_count++;
                if (this.photolinkOk(dl, null)) {
                    success = true;
                    break;
                }
            }
        }
        if (links_count == 0) {
            logger.warning("Found no possible downloadlink for current picturelink --> Plugin broken");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else if (links_count > 0 && !success) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Photo is temporarily unavailable or offline (server issues)", 30 * 60 * 1000l);
        }
    }

    /**
     * Try to get best quality and test links till a working link is found as it can happen that the found link is offline but others are
     * online. This function is made to check the information which has been saved via decrypter as the property "directlinks" on the
     * DownloadLink.
     *
     * @throws IOException
     */
    @SuppressWarnings({ "unused", "unchecked" })
    private void getHighestQualityPicFromSavedJson(final DownloadLink dl, final Object o) throws Exception {
        if (o == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        boolean success = false;
        /* Count how many possible downloadlinks we have */
        int links_count = 0;
        Map<String, Object> attachments = (Map<String, Object>) o;
        final String qualities[] = { "src_big", "src", "src_small" };
        for (final String quality : qualities) {
            final Object finurl = attachments.get(quality);
            if (finurl != null) {
                links_count++;
                this.finalUrl = finurl.toString();
                if (this.photolinkOk(dl, null)) {
                    success = true;
                    break;
                }
            } else {
                continue;
            }
        }
        if (links_count == 0) {
            logger.warning("Found no possible downloadlink for current picturelink --> Plugin broken");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else if (links_count > 0 && !success) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Photo is temporarily unavailable or offline (server issues)", 30 * 60 * 1000l);
        }
    }

    /**
     * Changes server of picture links if wished by user - if not it will change them back to their "original" format. On error (server does
     * not match expected) it won't touch the current finallink at all! Only use this for photo links!
     */
    private void photo_correctLink() {
        if (this.getPluginConfig().getBooleanProperty(VKPHOTO_CORRECT_FINAL_LINKS, false)) {
            logger.info("VKPHOTO_CORRECT_FINAL_LINKS enabled --> Correcting finallink");
            if (this.finalUrl.matches("https://pp\\.vk\\.me/c\\d+/.+")) {
                logger.info("final link is already in desired format --> Doing nothing");
            } else {
                /*
                 * Correct server to get files that are otherwise inaccessible - note that this can also make the finallinks unusable (e.g.
                 * server returns errorcode 500 instead of the file) but this is a very rare problem.
                 */
                final String oldserver = new Regex(this.finalUrl, "(https?://cs\\d+\\.vk\\.me/)").getMatch(0);
                final String serv_id = new Regex(this.finalUrl, "cs(\\d+)\\.vk\\.me/").getMatch(0);
                if (oldserver != null && serv_id != null) {
                    final String newserver = "https://pp.vk.me/c" + serv_id + "/";
                    this.finalUrl = this.finalUrl.replace(oldserver, newserver);
                    logger.info("VKPHOTO_CORRECT_FINAL_LINKS enabled --> SUCCEEDED to correct finallink");
                } else {
                    logger.warning("VKPHOTO_CORRECT_FINAL_LINKS enabled --> FAILED to correct finallink");
                }
            }
        } else {
            logger.info("VKPHOTO_CORRECT_FINAL_LINKS DISABLED --> changing final link back to standard");
            if (this.finalUrl.matches("http://cs\\d+\\.vk\\.me/v\\d+/.+")) {
                logger.info("final link is already in desired format --> Doing nothing");
            } else {
                /* Correct links to standard format */
                final Regex dataregex = new Regex(this.finalUrl, "(https?://pp\\.vk\\.me/c)(\\d+)/v(\\d+)/");
                final String serv_id = dataregex.getMatch(1);
                final String oldserver = dataregex.getMatch(0) + serv_id + "/";
                if (oldserver != null && serv_id != null) {
                    final String newserver = "http://cs" + serv_id + ".vk.me/";
                    this.finalUrl = this.finalUrl.replace(oldserver, newserver);
                    logger.info("VKPHOTO_CORRECT_FINAL_LINKS DISABLE --> SUCCEEDED to revert corrected finallink");
                } else {
                    logger.warning("VKPHOTO_CORRECT_FINAL_LINKS enabled --> FAILED to revert corrected finallink");
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private String getPhotoID(final DownloadLink dl) {
        return new Regex(dl.getDownloadURL(), "vkontaktedecrypted\\.ru/picturelink/((\\-)?[\\d\\-]+_[\\d\\-]+)").getMatch(0);
    }

    private void setConstants(final DownloadLink dl) {
        this.ownerID = getOwnerID(dl);
        this.contentID = getContentID(dl);
        this.mainlink = dl.getStringProperty("mainlink", null);
    }

    public static String getAudioAlbumPostString(final String source_url, final String ownerID) {
        String postData;
        if (new Regex(source_url, ".+vk\\.com/audio\\?id=\\-\\d+").matches()) {
            postData = "act=load_audios_silent&al=1&edit=0&id=0&gid=" + ownerID;
        } else {
            postData = "act=load_audios_silent&al=1&edit=0&gid=0&id=" + ownerID + "&please_dont_ddos=2";
        }
        return postData;
    }

    public static String[] getAudioDataArray(final Browser br) {
        final String completeData = br.getRegex("\\{\"all\":\\[(\\[.*?\\])\\]\\}").getMatch(0);
        if (completeData == null) {
            return null;
        }
        return completeData.split(",\\[");
    }

    private String getOwnerID(final DownloadLink dl) {
        return dl.getStringProperty("owner_id", null);
    }

    private String getContentID(final DownloadLink dl) {
        return dl.getStringProperty("content_id", null);
    }

    private boolean isJDStable() {
        return System.getProperty("jd.revision.jdownloaderrevision") == null;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    @Override
    public String getDescription() {
        return "JDownloader's Vk Plugin helps downloading all sorts of media from vk.com.";
    }

    /* Default values... */
    private static final boolean default_USECOOKIELOGIN                     = false;
    private static final boolean default_fastlinkcheck_FASTLINKCHECK        = true;
    private static final boolean default_fastlinkcheck_FASTPICTURELINKCHECK = true;
    private static final boolean default_fastlinkcheck_FASTAUDIOLINKCHECK   = true;
    private static final boolean default_ALLOW_BEST                         = false;
    private static final boolean default_ALLOW_240p                         = true;
    private static final boolean default_ALLOW_360p                         = true;
    private static final boolean default_ALLOW_480p                         = true;
    private static final boolean default_ALLOW_720p                         = true;
    private static final boolean default_WALL_ALLOW_albums                  = true;
    private static final boolean default_WALL_ALLOW_photo                   = true;
    private static final boolean default_WALL_ALLOW_audio                   = true;
    private static final boolean default_WALL_ALLOW_video                   = true;
    private static final boolean default_WALL_ALLOW_links                   = false;
    private static final boolean default_VKVIDEO_USEIDASPACKAGENAME         = false;
    private static final boolean default_VKAUDIO_USEIDASPACKAGENAME         = false;
    private static final boolean default_VKPHOTO_CORRECT_FINAL_LINKS        = false;
    public static final String   default_user_agent                         = "Mozilla/5.0 (Windows NT 6.3; rv:36.0) Gecko/20100101 Firefox/36.0";

    public void setConfigElements() {
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "General settings:"));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), USECOOKIELOGIN, JDL.L("plugins.hoster.vkontakteruhoster.alwaysUseCookiesForLogin", "Always use cookies for login (this can cause out of date errors)")).setDefaultValue(default_USECOOKIELOGIN));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Linkcheck settings:"));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), FASTLINKCHECK_VIDEO, JDL.L("plugins.hoster.vkontakteruhoster.fastLinkcheck", "Fast linkcheck for video links (filesize won't be shown in linkgrabber)?")).setDefaultValue(default_fastlinkcheck_FASTLINKCHECK));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), FASTLINKCHECK_PICTURES, JDL.L("plugins.hoster.vkontakteruhoster.fastPictureLinkcheck", "Fast linkcheck for picture links (filesize won't be shown in linkgrabber)?")).setDefaultValue(default_fastlinkcheck_FASTPICTURELINKCHECK));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), FASTLINKCHECK_AUDIO, JDL.L("plugins.hoster.vkontakteruhoster.fastAudioLinkcheck", "Fast linkcheck for audio links (filesize won't be shown in linkgrabber)?")).setDefaultValue(default_fastlinkcheck_FASTAUDIOLINKCHECK));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Video settings:"));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        final ConfigEntry hq = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.ALLOW_BEST, JDL.L("plugins.hoster.vkontakteruhoster.checkbest", "Only grab the best available resolution")).setDefaultValue(default_ALLOW_BEST);
        this.getConfig().addEntry(hq);
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.ALLOW_240P, JDL.L("plugins.hoster.vkontakteruhoster.check240", "Grab 240p MP4/FLV?")).setDefaultValue(default_ALLOW_240p).setEnabledCondidtion(hq, false));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.ALLOW_360P, JDL.L("plugins.hoster.vkontakteruhoster.check360", "Grab 360p MP4?")).setDefaultValue(default_ALLOW_360p).setEnabledCondidtion(hq, false));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.ALLOW_480P, JDL.L("plugins.hoster.vkontakteruhoster.check480", "Grab 480p MP4?")).setDefaultValue(default_ALLOW_480p).setEnabledCondidtion(hq, false));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.ALLOW_720P, JDL.L("plugins.hoster.vkontakteruhoster.check720", "Grab 720p MP4?")).setDefaultValue(default_ALLOW_720p).setEnabledCondidtion(hq, false));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Settings for 'vk.com/wall-123...' and 'vk.com/wall-123..._123...' links:\r\n NOTE: You can't turn off all types. If you do that, JD will decrypt all instead!"));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.VKWALL_GRAB_ALBUMS, JDL.L("plugins.hoster.vkontakteruhoster.wallcheckalbums", "Grab album links ('vk.com/album')?")).setDefaultValue(default_WALL_ALLOW_albums));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.VKWALL_GRAB_PHOTOS, JDL.L("plugins.hoster.vkontakteruhoster.wallcheckphotos", "Grab photo links ('vk.com/photo')?")).setDefaultValue(default_WALL_ALLOW_photo));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.VKWALL_GRAB_AUDIO, JDL.L("plugins.hoster.vkontakteruhoster.wallcheckaudio", "Grab audio links (.mp3 directlinks)?")).setDefaultValue(default_WALL_ALLOW_audio));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.VKWALL_GRAB_VIDEO, JDL.L("plugins.hoster.vkontakteruhoster.wallcheckvideo", "Grab video links ('vk.com/video')?")).setDefaultValue(default_WALL_ALLOW_video));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.VKWALL_GRAB_LINK, JDL.L("plugins.hoster.vkontakteruhoster.wallchecklink", "Grab links?\r\nA link is e.g. a hyperlink inside a users' wall post.")).setDefaultValue(default_WALL_ALLOW_links));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.VKWALL_GRAB_DOCS, JDL.L("plugins.hoster.vkontakteruhoster.wallcheckdocs", "Grab documents?")).setDefaultValue(true));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Settings for 'vk.com/video' links:"));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.VKVIDEO_USEIDASPACKAGENAME, JDL.L("plugins.hoster.vkontakteruhoster.videoUseIdAsPackagename", "Use video-ID as packagename ('videoXXXX_XXXX' or 'video-XXXX_XXXX')?")).setDefaultValue(default_VKVIDEO_USEIDASPACKAGENAME));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Settings for 'vk.com/audios' links:"));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.VKAUDIOS_USEIDASPACKAGENAME, JDL.L("plugins.hoster.vkontakteruhoster.audiosUseIdAsPackagename", "Use audio-Owner-ID as packagename ('audiosXXXX' or 'audios-XXXX')?")).setDefaultValue(default_VKAUDIO_USEIDASPACKAGENAME));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Advanced settings:\r\n<html><p style=\"color:#F62817\">WARNING: Only change these settings if you really know what you're doing!</p></html>"));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, this.getPluginConfig(), VKontakteRuHoster.VKPHOTO_CORRECT_FINAL_LINKS, JDL.L("plugins.hoster.vkontakteruhoster.correctFinallinks", "For 'vk.com/photo' links: Change final downloadlinks from 'https?://csXXX.vk.me/vXXX/...' to 'https://pp.vk.me/cXXX/vXXX/...' (forces HTTPS)?")).setDefaultValue(default_VKPHOTO_CORRECT_FINAL_LINKS));
        this.getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), VKADVANCED_USER_AGENT, JDL.L("plugins.hoster.vkontakteruhoster.customUserAgent", "User-Agent: ")).setDefaultValue(default_user_agent));
    }

}