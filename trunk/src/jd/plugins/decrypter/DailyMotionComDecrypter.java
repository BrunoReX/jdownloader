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

package jd.plugins.decrypter;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.hoster.DummyScriptEnginePlugin;
import jd.utils.JDUtilities;

import org.appwork.utils.formatter.TimeFormatter;

//Decrypts embedded videos from dailymotion
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "dailymotion.com" }, urls = { "https?://(?:www\\.)?dailymotion\\.com/.+" }, flags = { 0 })
public class DailyMotionComDecrypter extends PluginForDecrypt {

    public DailyMotionComDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String                          VIDEOSOURCE       = null;
    /**
     * @ 1hd1080URL or stream_h264_hd1080_url [1920x1080]
     * 
     * @ 2 hd720URL or stream_h264_hd_url [1280x720]
     * 
     * @ 3 hqURL or stream_h264_hq_url [848x480]
     * 
     * @ 4 sdURL or stream_h264_url [512x384]
     * 
     * @ 5 ldURL or video_url or stream_h264_ld_url [320x240]
     * 
     * @ 6 video_url or rtmp
     * 
     * @ 7 hds
     * 
     * @String[] = {"Direct download url", "filename, if available before quality selection"}
     */
    private LinkedHashMap<String, String[]> FOUNDQUALITIES    = new LinkedHashMap<String, String[]>();
    private String                          FILENAME          = null;
    private String                          PARAMETER         = null;

    private static final String             ALLOW_LQ          = "ALLOW_LQ";
    private static final String             ALLOW_SD          = "ALLOW_SD";
    private static final String             ALLOW_HQ          = "ALLOW_HQ";
    private static final String             ALLOW_720         = "ALLOW_720";
    private static final String             ALLOW_1080        = "ALLOW_1080";
    private static final String             ALLOW_OTHERS      = "ALLOW_OTHERS";
    public static final String              ALLOW_AUDIO       = "ALLOW_AUDIO";
    private static final String             ALLOW_HDS         = "ALLOW_HDS";

    private static final String             TYPE_PLAYLIST     = "https?://(?:www\\.)?dailymotion\\.com/playlist/[A-Za-z0-9]+_[A-Za-z0-9\\-_]+(/\\d+)?";
    private static final String             TYPE_USER         = "https?://(?:www\\.)?dailymotion\\.com/user/[A-Za-z0-9_\\-]+/\\d+";
    private static final String             TYPE_USER_SEARCH  = "https?://(?:www\\.)?dailymotion\\.com/.*?/user/[^/]+/search/[^/]+/\\d+";
    private static final String             TYPE_VIDEO        = "https?://(?:www\\.)?dailymotion\\.com/((?:embed/)?video/[A-Za-z0-9\\-_]+(\\?.+)?|swf(?:/video)?/[A-Za-z0-9]+)";

    private static final String             REGEX_VIDEOURLS   = "preview_link[\t\n\r ]*?\"[\t\n\r ]*?href=\"(/video/[^<>\"/]+)\"";

    public final static boolean             defaultAllowAudio = true;

    private ArrayList<DownloadLink>         decryptedLinks    = new ArrayList<DownloadLink>();

    private boolean                         acc_in_use        = false;

    private static AtomicBoolean            pluginLoaded      = new AtomicBoolean(false);
    private static Object                   ctrlLock          = new Object();

    /**
     * JD2 CODE: DO NOIT USE OVERRIDE FÒR COMPATIBILITY REASONS!!!!!
     */
    public boolean isProxyRotationEnabledForLinkCrawler() {
        return false;
    }

    @SuppressWarnings("deprecation")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        PARAMETER = param.toString().replace("www.", "").replace("embed/video/", "video/").replaceAll("\\.com/swf(/video)?/", ".com/video/").replace("https://", "http://");
        br.setFollowRedirects(true);

        synchronized (ctrlLock) {
            /* Login if account available */
            final PluginForHost dailymotionHosterplugin = JDUtilities.getPluginForHost("dailymotion.com");
            pluginLoaded.set(true);
            Account aa = AccountController.getInstance().getValidAccount(dailymotionHosterplugin);
            if (aa != null) {
                try {
                    ((jd.plugins.hoster.DailyMotionCom) dailymotionHosterplugin).login(aa, this.br);
                    acc_in_use = true;
                } catch (final PluginException e) {
                    logger.info("Account seems to be invalid -> Continuing without account!");
                }
            }
            /* Login end... */

            br.setCookie("http://www.dailymotion.com", "family_filter", "off");
            br.setCookie("http://www.dailymotion.com", "ff", "off");
            br.setCookie("http://www.dailymotion.com", "lang", "en_US");
            try {
                br.getPage(PARAMETER);
            } catch (final Exception e) {
                final DownloadLink dl = this.createOfflinelink(PARAMETER);
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
            // 404
            if (br.containsHTML("(<title>Dailymotion \\– 404 Not Found</title>|url\\(/images/404_background\\.jpg)") || this.br.getHttpConnection().getResponseCode() == 404) {
                final DownloadLink dl = this.createOfflinelink(PARAMETER);
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
            // 403
            if (br.containsHTML("class=\"forbidden\">Access forbidden</h3>|>You don\\'t have permission to access the requested URL") || this.br.getHttpConnection().getResponseCode() == 403) {
                final DownloadLink dl = this.createOfflinelink(PARAMETER);
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
            if (PARAMETER.matches(TYPE_PLAYLIST)) {
                decryptPlaylist();
            } else if (PARAMETER.matches(TYPE_USER) || br.containsHTML("class=\"user_header__details\"")) {
                decryptUser();
            } else if (PARAMETER.matches(TYPE_VIDEO)) {
                decryptSingleVideo(decryptedLinks);
            } else if (PARAMETER.matches(TYPE_USER_SEARCH)) {
                decryptUserSearch();
            } else {
                logger.info("Unsupported linktype: " + PARAMETER);
                return decryptedLinks;
            }
        }
        if (decryptedLinks == null) {
            logger.warning("Decrypter broken for link: " + PARAMETER);
            return null;
        }
        return decryptedLinks;
    }

    private void decryptUser() throws IOException {
        logger.info("Decrypting user: " + PARAMETER);
        String username = new Regex(PARAMETER, "dailymotion\\.com/user/([A-Za-z0-9\\-_]+)").getMatch(0);
        if (username == null) {
            username = new Regex(PARAMETER, "dailymotion\\.com/([A-Za-z0-9_\\-]+)").getMatch(0);
        }
        br.getPage("http://www.dailymotion.com/" + username);
        if (br.containsHTML("class=\"dmco_text nothing_to_see\"")) {
            final DownloadLink dl = createDownloadlink("http://dailymotiondecrypted.com/video/" + System.currentTimeMillis());
            dl.setContentUrl(PARAMETER);
            dl.setFinalFileName(username);
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return;
        }
        String fpName = br.getRegex("class=\"mrg-end-sm user-screenname-inner\">([^<>\"]*?)</span>").getMatch(0);
        if (fpName == null) {
            fpName = username;
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        String videosNum = br.getRegex(Pattern.compile("<span class=\"font\\-xl mrg\\-end\\-xs\">(\\d+(?:,\\d+)?)</span>[\t\n\r ]+Videos?[\t\n\r ]+</div>", Pattern.CASE_INSENSITIVE)).getMatch(0);
        if (videosNum == null) {
            videosNum = br.getRegex("class=\"font-xl mrg-end-xs\">(\\d+(?:,\\d+)?)</span> Videos?").getMatch(0);
        }
        if (videosNum == null) {
            logger.warning("dailymotion.com: decrypter failed: " + PARAMETER);
            decryptedLinks = null;
            return;
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);
        final int videoCount = Integer.parseInt(videosNum.replace(",", ""));
        if (videoCount == 0) {
            /* User has 0 videos */
            final DownloadLink dl = createDownloadlink("http://dailymotiondecrypted.com/video/" + System.currentTimeMillis());
            dl.setContentUrl(PARAMETER);
            dl.setFinalFileName(username);
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return;
        }
        String desiredPage = new Regex(PARAMETER, "/user/[A-Za-z0-9]+/(\\d+)$").getMatch(0);
        if (desiredPage == null) {
            desiredPage = "1";
        }
        boolean parsePageOnly = false;
        if (Integer.parseInt(desiredPage) != 1) {
            parsePageOnly = true;
        }
        int currentPage = Integer.parseInt(desiredPage);
        final BigDecimal bd = new BigDecimal((double) videoCount / 18);
        final int pagesNum = bd.setScale(0, BigDecimal.ROUND_UP).intValue();
        do {
            if (this.isAbort()) {
                logger.info("dailymotion.com: Decrypt process aborted by user on page " + currentPage + " of " + pagesNum);
                return;
            }
            logger.info("Decrypting page " + currentPage + " / " + pagesNum);
            br.getPage("http://www.dailymotion.com/user/" + username + "/" + currentPage);
            final String[] videos = br.getRegex(REGEX_VIDEOURLS).getColumn(0);
            if (videos == null || videos.length == 0) {
                logger.info("Found no videos on page " + currentPage + " -> Stopping decryption");
                break;
            }
            for (final String videolink : videos) {
                final DownloadLink fina = createDownloadlink("http://www.dailymotion.com" + videolink);
                fp.add(fina);
                distribute(fina);
                decryptedLinks.add(fina);
            }
            logger.info("dailymotion.com: Decrypted page " + currentPage + " of " + pagesNum);
            logger.info("dailymotion.com: Found " + videos.length + " links on current page");
            logger.info("dailymotion.com: Found " + decryptedLinks.size() + " of total " + videoCount + " links already...");
            currentPage++;
        } while (decryptedLinks.size() < videoCount && !parsePageOnly);
        if (decryptedLinks == null || decryptedLinks.size() == 0) {
            logger.warning("Dailymotion.com decrypter failed: " + PARAMETER);
            decryptedLinks = null;
            return;
        }
        fp.addLinks(decryptedLinks);
    }

    private void decryptPlaylist() throws IOException {
        logger.info("Decrypting playlist: " + PARAMETER);
        final Regex info = br.getRegex("class=\"name\">([^<>\"]*?)</a> \\| (\\d+(,\\d+)?) Videos?");
        String username = info.getMatch(0);
        if (username == null) {
            username = br.getRegex("<meta name=\"author\" content=\"([^<>\"]*?)\"").getMatch(0);
        }
        String fpName = br.getRegex("<div id=\"playlist_name\">([^<>\"]*?)</div>").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("<div class=\"page\\-title mrg\\-btm\\-sm\">([^<>\"]*?)</div>").getMatch(0);
        }
        if (fpName == null) {
            fpName = br.getRegex("\"playlist_title\":\"([^<>\"]*?)\"").getMatch(0);
        }
        if (fpName == null) {
            fpName = new Regex(PARAMETER, "dailymotion.com/playlist/([A-Za-z0-9]+_[A-Za-z0-9\\-_]+)").getMatch(0);
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        String videosNum = info.getMatch(1);
        final String videosnum_text = br.getRegex("class=\"link\\-on\\-hvr\"(.*?)<span>").getMatch(0);
        if (videosNum == null && videosnum_text != null) {
            videosNum = new Regex(videosnum_text, "(\\d+(,\\d+)?) Videos?").getMatch(0);
        }
        if (videosNum == null) {
            /* Empty playlist site */
            if (!br.containsHTML("\"watchlaterAdd\"")) {
                final DownloadLink dl = createDownloadlink("directhttp://" + PARAMETER);
                dl.setContentUrl(PARAMETER);
                dl.setFinalFileName(fpName);
                dl.setProperty("offline", true);
                decryptedLinks.add(dl);
                return;
            }
            logger.warning("dailymotion.com: decrypter failed: " + PARAMETER);
            decryptedLinks = null;
            return;
        }
        final FilePackage fp = FilePackage.getInstance();
        fpName = Encoding.htmlDecode(username).trim() + " - " + Encoding.htmlDecode(fpName).trim();
        fp.setName(fpName);
        final int videoCount = Integer.parseInt(videosNum.replace(",", ""));
        if (videoCount == 0) {
            /* User has 0 videos */
            final DownloadLink dl = createDownloadlink("directhttp://" + PARAMETER);
            dl.setFinalFileName(username);
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return;
        }
        String desiredPage = new Regex(PARAMETER, "playlist/[A-Za-z0-9]+_[A-Za-z0-9\\-_]+/(\\d+)").getMatch(0);
        if (desiredPage == null) {
            desiredPage = "1";
        }
        boolean parsePageOnly = false;
        if (Integer.parseInt(desiredPage) != 1) {
            parsePageOnly = true;
        }
        final BigDecimal bd = new BigDecimal((double) videoCount / 18);
        final int pagesNum = bd.setScale(0, BigDecimal.ROUND_UP).intValue();

        int currentPage = Integer.parseInt(desiredPage);
        final String base_link = "http://www.dailymotion.com/playlist/" + new Regex(PARAMETER, "/playlist/([a-z0-9\\-_]+)/?").getMatch(0);
        do {
            try {
                if (this.isAbort()) {
                    logger.info("dailymotion.com: Decrypt process aborted by user on page " + currentPage + " of " + pagesNum);
                    return;
                }
            } catch (final Throwable e) {
                // Not available in 0.9.581
            }
            final String nextpage = base_link + "/" + currentPage;
            logger.info("Decrypting page: " + nextpage);
            br.getPage(nextpage);
            final String[] videos = br.getRegex(REGEX_VIDEOURLS).getColumn(0);
            if (videos == null || videos.length == 0) {
                logger.info("Found no videos on page " + currentPage + " -> Stopping decryption");
                break;
            }
            for (final String videolink : videos) {
                final DownloadLink fina = createDownloadlink("http://www.dailymotion.com" + videolink);
                fp.add(fina);
                try {
                    distribute(fina);
                } catch (final Throwable e) {
                    // Not available in 0.9.581
                }
                decryptedLinks.add(fina);
            }
            logger.info("dailymotion.com: Decrypted page " + currentPage + " of " + pagesNum);
            logger.info("dailymotion.com: Found " + videos.length + " links on current page");
            logger.info("dailymotion.com: Found " + decryptedLinks.size() + " of total " + videoCount + " links already...");
            currentPage++;
        } while (decryptedLinks.size() < videoCount && !parsePageOnly);
        if (decryptedLinks == null || decryptedLinks.size() == 0) {
            logger.warning("Dailymotion.com decrypter failed: " + PARAMETER);
            decryptedLinks = null;
            return;
        }
        fp.addLinks(decryptedLinks);
    }

    private void decryptUserSearch() throws IOException {
        int pagesNum = 1;
        final String[] page_strs = this.br.getRegex("class=\"foreground2 inverted-link-on-hvr\"> ?(\\d+)</a>").getColumn(0);
        if (page_strs != null) {
            for (final String page_str : page_strs) {
                final int page_int = Integer.parseInt(page_str);
                if (page_int > pagesNum) {
                    pagesNum = page_int;
                }
            }
        }
        final String main_search_url = new Regex(PARAMETER, "(.+/)\\d+$").getMatch(0);
        final String username = new Regex(PARAMETER, "/user/([^/]+)/").getMatch(0);
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(username);
        String desiredPage = new Regex(PARAMETER, "(\\d+)$").getMatch(0);
        if (desiredPage == null) {
            desiredPage = "1";
        }
        boolean parsePageOnly = false;
        if (Integer.parseInt(desiredPage) != 1) {
            parsePageOnly = true;
        }
        int currentPage = Integer.parseInt(desiredPage);
        do {
            if (this.isAbort()) {
                logger.info("dailymotion.com: Decrypt process aborted by user on page " + currentPage + " of " + pagesNum);
                return;
            }
            logger.info("Decrypting page " + currentPage + " / " + pagesNum);
            br.getPage(main_search_url + currentPage);
            final String[] videos = br.getRegex("<a href=\"(/video/[^<>\"]*?)\" class=\"link\"").getColumn(0);
            if (videos == null || videos.length == 0) {
                logger.info("Found no videos on page " + currentPage + " -> Stopping decryption");
                break;
            }
            for (final String videolink : videos) {
                final DownloadLink fina = createDownloadlink("http://www.dailymotion.com" + videolink);
                fp.add(fina);
                distribute(fina);
                decryptedLinks.add(fina);
            }
            logger.info("dailymotion.com: Decrypted page " + currentPage + " of " + pagesNum);
            logger.info("dailymotion.com: Found " + videos.length + " links on current page");
            currentPage++;
        } while (currentPage <= pagesNum && !parsePageOnly);

        if (this.decryptedLinks.size() == 0) {
            logger.info("Found nothing - user probably entered invalid search term(s)");
        }
    }

    private String VIDEOID     = null;
    private String CHANNELNAME = null;
    private long   DATE        = 0;

    @SuppressWarnings("deprecation")
    protected void decryptSingleVideo(ArrayList<DownloadLink> decryptedLinks) throws IOException, ParseException {
        logger.info("Decrypting single video: " + PARAMETER);
        // We can't download livestreams
        if (br.containsHTML("DMSTREAMMODE=live")) {
            final DownloadLink dl = createDownloadlink(PARAMETER.replace("dailymotion.com/", "dailymotiondecrypted.com/"));
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return;
        }
        /** Decrypt start */
        /** Decrypt external links START */
        String externID = br.getRegex("player\\.hulu\\.com/express/(\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.hulu.com/watch/" + externID));
            return;
        }
        externID = br.getRegex("name=\"movie\" value=\"(http://(www\\.)?embed\\.5min\\.com/\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return;
        }
        externID = br.getRegex("\"(http://videoplayer\\.vevo\\.com/embed/embedded\\?videoId=[A-Za-z0-9]+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return;
        }

        /** Decrypt external links END */
        /** Find videolinks START */
        VIDEOID = new Regex(PARAMETER, "dailymotion\\.com/video/([a-z0-9]+)").getMatch(0);
        CHANNELNAME = br.getRegex("\"owner\":\"([^<>\"]*?)\"").getMatch(0);
        String strdate = br.getRegex("property=\"video:release_date\" content=\"([^<>\"]*?)\"").getMatch(0);
        FILENAME = br.getRegex("<meta itemprop=\"name\" content=\"([^<>\"]*?)\"").getMatch(0);
        if (FILENAME == null) {
            FILENAME = br.getRegex("<meta property=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
        }
        VIDEOSOURCE = getVideosource(this.br);
        if (VIDEOSOURCE == null || FILENAME == null || VIDEOID == null || CHANNELNAME == null || strdate == null) {
            logger.warning("Dailymotion.com decrypter failed: " + PARAMETER);
            final DownloadLink dl = this.createOfflinelink(PARAMETER);
            dl.setFinalFileName(new Regex(PARAMETER, "dailymotion\\.com/(.+)").getMatch(0));
            dl.setProperty("offline", true);
            decryptedLinks.add(dl);
            return;
        }

        /* Fix date */
        strdate = strdate.replace("T", "").replace("+", "GMT");
        DATE = TimeFormatter.getMilliSeconds(strdate, "yyyy-MM-ddHH:mm:ssz", Locale.ENGLISH);

        FILENAME = Encoding.htmlDecode(FILENAME.trim()).replace(":", " - ").replaceAll("/|<|>", "");
        if (new Regex(VIDEOSOURCE, "(Dein Land nicht abrufbar|this content is not available for your country|This video has not been made available in your country by the owner|\"Video not available due to geo\\-restriction)").matches()) {
            final DownloadLink dl = createDownloadlink("directhttp://" + PARAMETER);
            dl.setContentUrl(PARAMETER);
            dl.setFinalFileName("Geo restricted video - " + FILENAME + ".mp4");
            dl.setProperty("countryblock", true);
            dl.setAvailable(true);
            decryptedLinks.add(dl);
            return;
        } else if (new Regex(VIDEOSOURCE, "\"title\":\"Video geo\\-restricted by the owner").matches()) {
            final DownloadLink dl = createDownloadlink("directhttp://" + PARAMETER);
            dl.setContentUrl(PARAMETER);
            dl.setFinalFileName("Geo-Restricted by owner - " + FILENAME + ".mp4");
            dl.setProperty("offline", true);
            dl.setAvailable(false);
            decryptedLinks.add(dl);
        } else if (new Regex(VIDEOSOURCE, "(his content as suitable for mature audiences only|You must be logged in, over 18 years old, and set your family filter OFF, in order to watch it)").matches() && !acc_in_use) {
            final DownloadLink dl = createDownloadlink("directhttp://" + PARAMETER);
            dl.setContentUrl(PARAMETER);
            dl.setFinalFileName(FILENAME + ".mp4");
            dl.setProperty("registeredonly", true);
            dl.setAvailable(true);
            decryptedLinks.add(dl);
            return;
        } else if (new Regex(VIDEOSOURCE, "\"message\":\"Publication of this video is in progress").matches()) {
            final DownloadLink dl = createDownloadlink("directhttp://" + PARAMETER);
            dl.setContentUrl(PARAMETER);
            dl.setFinalFileName("Publication of this video is in progress - " + FILENAME + ".mp4");
            dl.setProperty("offline", true);
            dl.setAvailable(false);
            decryptedLinks.add(dl);
            return;
        } else if (new Regex(VIDEOSOURCE, "\"encodingMessage\":\"Encoding in progress\\.\\.\\.\"").matches()) {
            final DownloadLink dl = createDownloadlink("directhttp://" + PARAMETER);
            dl.setContentUrl(PARAMETER);
            dl.setFinalFileName("Encoding in progress - " + FILENAME + ".mp4");
            dl.setProperty("offline", true);
            dl.setAvailable(false);
            decryptedLinks.add(dl);
            return;
        } else if (new Regex(VIDEOSOURCE, "\"title\":\"Channel offline\\.\"").matches()) {
            final DownloadLink dl = createDownloadlink("directhttp://" + PARAMETER);
            dl.setContentUrl(PARAMETER);
            dl.setFinalFileName("Channel offline - " + FILENAME + ".mp4");
            dl.setProperty("offline", true);
            dl.setAvailable(false);
            decryptedLinks.add(dl);
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(FILENAME);

        /** Decrypt subtitles if available */
        String[] subtitles = new Regex(VIDEOSOURCE, "\"(http://static\\d+\\.dmcdn\\.net/static/video/\\d+/\\d+/\\d+:subtitle_[a-z]{1,4}\\.srt(?:\\?\\d+)?)\"").getColumn(0);
        if (subtitles != null && subtitles.length != 0) {
            final FilePackage fpSub = FilePackage.getInstance();
            fpSub.setName(FILENAME + "_Subtitles");
            for (final String subtitle : subtitles) {
                final DownloadLink dl = createDownloadlink("http://dailymotiondecrypted.com/video/" + System.currentTimeMillis() + new Random().nextInt(10000));
                dl.setContentUrl(PARAMETER);
                final String language = new Regex(subtitle, ".*?\\d+:subtitle_(.{1,4}).srt.*?").getMatch(0);
                String qualityname = "subtitle";
                if (language != null) {
                    qualityname += "_" + language;
                }
                dl.setProperty("directlink", subtitle);
                dl.setProperty("type_subtitle", true);
                dl.setProperty("qualityname", qualityname);
                dl.setProperty("mainlink", PARAMETER);
                dl.setProperty("plain_videoname", FILENAME);
                dl.setProperty("plain_ext", ".srt");
                dl.setProperty("plain_videoid", VIDEOID);
                dl.setProperty("plain_channel", CHANNELNAME);
                dl.setProperty("plain_date", Long.toString(DATE));
                dl.setLinkID("dailymotioncom" + VIDEOID + "_" + qualityname);
                final String formattedFilename = jd.plugins.hoster.DailyMotionCom.getFormattedFilename(dl);
                dl.setName(formattedFilename);
                fpSub.add(dl);
                decryptedLinks.add(dl);
            }
        }

        FOUNDQUALITIES = findVideoQualities(this.br, PARAMETER, VIDEOSOURCE);
        if (FOUNDQUALITIES.isEmpty() && decryptedLinks.size() == 0) {
            logger.warning("Found no quality for link: " + PARAMETER);
            decryptedLinks = null;
            return;
        }
        /** Find videolinks END */
        /** Pick qualities, selected by the user START */
        final ArrayList<String> selectedQualities = new ArrayList<String>();
        final SubConfiguration cfg = SubConfiguration.getConfig("dailymotion.com");
        if (cfg.getBooleanProperty("ALLOW_BEST", false)) {
            for (final String quality : new String[] { "1", "2", "7", "6", "3", "4", "5" }) {
                if (FOUNDQUALITIES.containsKey(quality)) {
                    selectedQualities.add(quality);
                    break;
                }
            }
        }
        if (cfg.getBooleanProperty("ALLOW_BEST", false) == false || selectedQualities.size() == 0) {
            boolean qld = cfg.getBooleanProperty(ALLOW_LQ, false);
            boolean qsd = cfg.getBooleanProperty(ALLOW_SD, false);
            boolean qhq = cfg.getBooleanProperty(ALLOW_HQ, false);
            boolean q720 = cfg.getBooleanProperty(ALLOW_720, false);
            boolean q1080 = cfg.getBooleanProperty(ALLOW_1080, false);
            boolean others = cfg.getBooleanProperty(ALLOW_OTHERS, false);
            boolean hds = cfg.getBooleanProperty(ALLOW_HDS, false);
            /** User selected nothing -> Decrypt everything */
            if (qld == false && qsd == false && qhq == false && q720 == false && q1080 == false && others == false && hds == false) {
                qld = true;
                qsd = true;
                qhq = true;
                q720 = true;
                q1080 = true;
                others = true;
                hds = true;
            }
            if (qld) {
                selectedQualities.add("5");
            }
            if (qsd) {
                selectedQualities.add("4");
            }
            if (qhq) {
                selectedQualities.add("3");
            }
            if (q720) {
                selectedQualities.add("2");
            }
            if (q1080) {
                selectedQualities.add("1");
            }
            if (others) {
                selectedQualities.add("6");
            }
            if (hds) {
                selectedQualities.add("7");
            }
        }
        for (final String selectedQuality : selectedQualities) {
            final DownloadLink dl = setVideoDownloadlink(this.br, FOUNDQUALITIES, selectedQuality);
            if (dl == null) {
                break;
            }
            dl.setContentUrl(PARAMETER);
            fp.add(dl);
            decryptedLinks.add(dl); // Needed only for the "if" below.
        }
        /** Pick qualities, selected by the user END */
        if (decryptedLinks.size() == 0) {
            logger.info("None of the selected qualities were found, decrypting done...");
            return;
        }
    }

    @SuppressWarnings("unchecked")
    public static LinkedHashMap<String, String[]> findVideoQualities(final Browser br, final String parameter, String videosource) throws IOException {
        LinkedHashMap<String, String[]> QUALITIES = new LinkedHashMap<String, String[]>();
        final String[][] qualities = { { "hd1080URL", "1" }, { "hd720URL", "2" }, { "hqURL", "3" }, { "sdURL", "4" }, { "ldURL", "5" }, { "video_url", "5" } };
        for (final String quality[] : qualities) {
            final String qualityName = quality[0];
            final String qualityNumber = quality[1];
            final String currentQualityUrl = getQuality(qualityName, videosource);
            if (currentQualityUrl != null) {
                final String[] dlinfo = new String[4];
                dlinfo[0] = currentQualityUrl;
                dlinfo[1] = null;
                dlinfo[2] = qualityName;
                dlinfo[3] = qualityNumber;
                QUALITIES.put(qualityNumber, dlinfo);
            }
        }
        if (QUALITIES.isEmpty() && videosource.startsWith("{\"context\"")) {
            /* "New" player July 2015 */
            try {
                LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(videosource);
                entries = (LinkedHashMap<String, Object>) DummyScriptEnginePlugin.walkJson(entries, "metadata/qualities");
                /* TODO: Maybe all HLS support in case it gives us more/other formats/qualities */
                final String[][] qualities_2 = { { "1080", "1" }, { "720", "2" }, { "480", "3" }, { "380", "4" }, { "240", "5" } };
                for (final String quality[] : qualities_2) {
                    final String qualityName = quality[0];
                    final String qualityNumber = quality[1];
                    final Object jsono = entries.get(qualityName);
                    final String currentQualityUrl = (String) DummyScriptEnginePlugin.walkJson(jsono, "{0}/url");
                    if (currentQualityUrl != null) {
                        final String[] dlinfo = new String[4];
                        dlinfo[0] = currentQualityUrl;
                        dlinfo[1] = null;
                        dlinfo[2] = qualityName;
                        dlinfo[3] = qualityNumber;
                        QUALITIES.put(qualityNumber, dlinfo);
                    }
                }
            } catch (final Throwable e) {
            }
        }
        // List empty or only 1 link found -> Check for (more) links
        if (QUALITIES.isEmpty() || QUALITIES.size() == 1) {
            final String manifestURL = new Regex(videosource, "\"autoURL\":\"(http://[^<>\"]*?)\"").getMatch(0);
            if (manifestURL != null) {
                /** HDS */
                final String[] dlinfo = new String[4];
                dlinfo[0] = manifestURL;
                dlinfo[1] = "hds";
                dlinfo[2] = "autoURL";
                dlinfo[3] = "7";
                QUALITIES.put("7", dlinfo);
            }

            // Try to avoid HDS
            br.getPage("http://www.dailymotion.com/embed/video/" + new Regex(parameter, "([A-Za-z0-9\\-_]+)$").getMatch(0));

            // 19.09.2014
            videosource = br.getRegex("(\"stream_.*)\"swf_url\":").getMatch(0);
            if (videosource == null) {
                // old version. did not work for me today (19.09.2014)
                videosource = br.getRegex("var info = \\{(.*?)\\},").getMatch(0);

            }
            if (videosource != null) {
                videosource = Encoding.htmlDecode(videosource).replace("\\", "");
                final String[][] embedQualities = { { "stream_h264_ld_url", "5" }, { "stream_h264_url", "4" }, { "stream_h264_hq_url", "3" }, { "stream_h264_hd_url", "2" }, { "stream_h264_hd1080_url", "1" } };
                for (final String quality[] : embedQualities) {
                    final String qualityName = quality[0];
                    final String qualityNumber = quality[1];
                    final String currentQualityUrl = getQuality(qualityName, videosource);
                    if (currentQualityUrl != null) {
                        final String[] dlinfo = new String[4];
                        dlinfo[0] = currentQualityUrl;
                        dlinfo[1] = null;
                        dlinfo[2] = qualityName;
                        dlinfo[3] = qualityNumber;
                        QUALITIES.put(qualityNumber, dlinfo);
                    }
                }
            }
            // if (FOUNDQUALITIES.isEmpty()) {
            // String[] values =
            // br.getRegex("new
            // SWFObject\\(\"(http://player\\.grabnetworks\\.com/swf/GrabOSMFPlayer\\.swf)\\?id=\\d+\\&content=v([0-9a-f]+)\"").getRow(0);
            // if (values == null || values.length != 2) {
            // /** RTMP */
            // final DownloadLink dl = createDownloadlink("http://dailymotiondecrypted.com/video/" + System.currentTimeMillis() + new
            // Random(10000));
            // dl.setProperty("isrtmp", true);
            // dl.setProperty("mainlink", PARAMETER);
            // dl.setFinalFileName(FILENAME + "_RTMP.mp4");
            // fp.add(dl);
            // decryptedLinks.add(dl);
            // return decryptedLinks;
            // }
            // }
        }
        return QUALITIES;
    }

    /* Sync the following functions in hoster- and decrypterplugin */
    public static String getVideosource(final Browser br) {
        String videosource = br.getRegex("\"sequence\":\"([^<>\"]*?)\"").getMatch(0);
        if (videosource == null) {
            videosource = br.getRegex("%2Fsequence%2F(.*?)</object>").getMatch(0);
        }
        if (videosource == null) {
            videosource = br.getRegex("name=\"flashvars\" value=\"(.*?)\"/></object>").getMatch(0);
        }
        if (videosource == null) {
            /*
             * This source is unsupported however we only need to have it here so the handling later will eventually fail and jump into
             * embed-fallback mode. See here (some users seem to get another/new videoplayer):
             * https://board.jdownloader.org/showthread.php?t=64943&page=2
             */
            videosource = br.getRegex("window\\.playerV5 = dmp\\.create\\(document\\.getElementById\\(\\'player\\'\\), (\\{.*?\\}\\})\\);").getMatch(0);
        }
        if (videosource == null) {
            // buildPlayer({"context": ... "ui":{}}});
            videosource = br.getRegex("buildPlayer\\((\\{\"context\":.*?\\}\\})\\);").getMatch(0);
        }
        return videosource;
    }

    @SuppressWarnings("deprecation")
    private DownloadLink setVideoDownloadlink(final Browser br, final LinkedHashMap<String, String[]> foundqualities, final String qualityValue) throws ParseException {
        String directlinkinfo[] = foundqualities.get(qualityValue);
        if (directlinkinfo != null) {
            final String directlink = Encoding.htmlDecode(directlinkinfo[0]);
            final DownloadLink dl = createDownloadlink("http://dailymotiondecrypted.com/video/" + System.currentTimeMillis() + new Random().nextInt(10000));
            String qualityName = directlinkinfo[1]; // qualityName is dlinfo[2]
            if (qualityName == null) {
                qualityName = new Regex(directlink, "cdn/([^<>\"]*?)/video").getMatch(0);
            }
            final String originalQualityName = directlinkinfo[2];
            final String qualityNumber = directlinkinfo[3];
            dl.setProperty("directlink", directlink);
            dl.setProperty("qualityvalue", qualityValue);
            dl.setProperty("qualityname", qualityName);
            dl.setProperty("originalqualityname", originalQualityName);
            dl.setProperty("qualitynumber", qualityNumber);
            dl.setProperty("mainlink", PARAMETER);
            dl.setProperty("plain_videoname", FILENAME);
            dl.setProperty("plain_ext", ".mp4");
            dl.setProperty("plain_videoid", VIDEOID);
            dl.setProperty("plain_channel", CHANNELNAME);
            dl.setProperty("plain_date", Long.toString(DATE));
            dl.setLinkID("dailymotioncom" + VIDEOID + "_" + qualityName);
            final String formattedFilename = jd.plugins.hoster.DailyMotionCom.getFormattedFilename(dl);
            dl.setName(formattedFilename);
            try {
                dl.setContentUrl(PARAMETER);
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
                dl.setBrowserUrl(PARAMETER);
            }
            logger.info("Creating: " + directlinkinfo[2] + "/" + qualityName + " link");
            decryptedLinks.add(dl); // This is it, not the other one.
            return dl;
        } else {
            return null;
        }
    }

    private static String getQuality(final String quality, final String videosource) {
        return new Regex(videosource, "\"" + quality + "\":\"(http[^<>\"\\']+)\"").getMatch(0);
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}