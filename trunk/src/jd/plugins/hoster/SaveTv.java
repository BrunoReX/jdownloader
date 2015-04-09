//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.Cookie;
import jd.http.Cookies;
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

import org.appwork.uio.UIOManager;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.swing.dialog.ContainerDialog;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogNoAnswerException;
import org.jdownloader.DomainInfo;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "save.tv" }, urls = { "https?://(www\\.)?save\\.tv/STV/M/obj/archive/VideoArchiveDetails\\.cfm\\?TelecastID=\\d+" }, flags = { 2 })
public class SaveTv extends PluginForHost {

    /* Static information */
    private final static String  APIKEY                                    = "Q0FFQjZDQ0YtMDdFNC00MDQ4LTkyMDQtOUU5QjMxOEU3OUIz";
    @SuppressWarnings("unused")
    private final String         APIPAGE                                   = "http://api.save.tv/v2/Api.svc?wsdl";
    public static final double   QUALITY_HD_MB_PER_MINUTE                  = 22;
    public static final double   QUALITY_H264_NORMAL_MB_PER_MINUTE         = 12.605;
    public static final double   QUALITY_H264_MOBILE_MB_PER_MINUTE         = 4.64;
    private final static String  COOKIE_HOST                               = "http://save.tv";
    private static final String  NICE_HOST                                 = "save.tv";
    /* Properties */
    private static final String  NICE_HOSTproperty                         = "savetv";
    private static final String  CRAWLER_PROPERTY_LASTCRAWL                = "CRAWLER_PROPERTY_LASTCRAWL";

    /* Settings stuff */
    private static final String  USEORIGINALFILENAME                       = "USEORIGINALFILENAME";
    private static final String  PREFERADSFREE                             = "PREFERADSFREE";
    private static final String  ADS_FREE_UNAVAILABLE_MAXRETRIES           = "ADS_FREE_UNAVAILABLE_MAXRETRIES";
    private static final String  FORCE_WITH_ADS_ON_ERROR_HOURS             = "FORCE_WITH_ADS_ON_ERROR_HOURS_2";
    private static final String  FORCE_WITH_ADS_ON_ERROR_MAXRETRIES        = "FORCE_WITH_ADS_ON_ERROR_HOURS_MAXRETRIES_3";
    private static final String  ADS_FREE_UNAVAILABLE_HOURS                = "DOWNLOADONLYADSFREE_RETRY_HOURS";
    private final String         ADSFREEAVAILABLETEXT                      = "Video ist werbefrei verfügbar";
    private final String         ADSFREEANOTVAILABLE                       = "Video ist nicht werbefrei verfügbar";
    private static final String  PREFERREDFORMATNOTAVAILABLETEXT           = "Das bevorzugte Format ist (noch) nicht verfügbar. Warte oder ändere die Einstellung!";
    private final String         NOCUTAVAILABLETEXT                        = "Für diese Sendung steht (noch) keine Schnittliste zur Verfügung";
    private final static String  selected_video_format                     = "selected_video_format";

    /* The list of server values displayed to the user */
    private final String[]       formats                                   = new String[] { "HD", "H.264 HQ", "H.264 MOBILE" };

    /* Crawler settings */
    private final static String  CRAWLER_ONLY_ADD_NEW_IDS                  = "CRAWLER_ONLY_ADD_NEW_IDS";
    private static final String  ACTIVATE_BETA_FEATURES                    = "ACTIVATE_BETA_FEATURES";
    private static final String  USEAPI                                    = "USEAPI";
    private static final String  CRAWLER_ACTIVATE                          = "CRAWLER_ACTIVATE";
    private static final String  CRAWLER_ENABLE_FASTER                     = "CRAWLER_ENABLE_FASTER_2";
    private static final String  CRAWLER_DISABLE_DIALOGS                   = "CRAWLER_DISABLE_DIALOGS";
    private static final String  CRAWLER_LASTHOURS_COUNT                   = "CRAWLER_LASTHOURS_COUNT";
    private static final String  DISABLE_LINKCHECK                         = "DISABLE_LINKCHECK";
    private static final String  DELETE_TELECAST_ID_AFTER_DOWNLOAD         = "DELETE_TELECAST_ID_AFTER_DOWNLOAD";
    private static final String  DELETE_TELECAST_ID_IF_FILE_ALREADY_EXISTS = "DELETE_TELECAST_ID_IF_FILE_ALREADY_EXISTS";

    /* Custom filename settings stuff */
    private static final String  CUSTOM_DATE                               = "CUSTOM_DATE";
    private static final String  CUSTOM_FILENAME_MOVIES                    = "CUSTOM_FILENAME_MOVIES";
    private static final String  CUSTOM_FILENAME_SERIES                    = "CUSTOM_FILENAME_SERIES_3";
    private static final String  CUSTOM_FILENAME_SEPERATION_MARK           = "CUSTOM_FILENAME_SEPERATION_MARK";
    private static final String  CUSTOM_FILENAME_EMPTY_TAG_STRING          = "CUSTOM_FILENAME_EMPTY_TAG_STRING";
    private static final String  FORCE_ORIGINALFILENAME_SERIES             = "FORCE_ORIGINALFILENAME_SERIES";
    private static final String  FORCE_ORIGINALFILENAME_MOVIES             = "FORCE_ORIGINALFILENAME_MOVIES";

    /* Variables */
    private boolean              FORCE_LINKCHECK                           = false;
    private boolean              ISADSFREEAVAILABLE                        = false;

    /* If this != null, API can be used */
    private String               API_SESSIONID                             = null;

    /* Other */
    private static Object        LOCK                                      = new Object();
    private static final int     MAX_RETRIES_LOGIN                         = 10;
    private static final int     MAX_RETRIES_SAFE_REQUEST                  = 3;

    /* Download connections constants */
    private static final boolean ACCOUNT_PREMIUM_RESUME                    = true;
    private static final int     ACCOUNT_PREMIUM_MAXCHUNKS                 = -2;
    private static final int     ACCOUNT_PREMIUM_MAXDOWNLOADS              = -1;

    /* Other API/site errorhandling constants */
    private static final String  SITE_DL_IMPOSSIBLE                        = ">Diese Sendung kann leider nicht heruntergeladen werden, da die Aufnahme fehlerhaft ist";
    private static final String  API_DL_IMPOSSIBLE                         = ">1418</ErrorCodeID>";
    private static final String  DL_IMPOSSIBLE_USER_TEXT                   = JDL.L("plugins.hoster.SaveTv.dlImpossible", "Aufnahme fehlerhaft - Download momentan nicht möglich");

    /* Property / Filename constants */
    public static final String   QUALITY_PARAM                             = "quality";
    public static final String   QUALITY_LQ                                = "LQ";
    public static final String   QUALITY_HQ                                = "HQ";
    public static final String   QUALITY_HD                                = "HD";
    public static final String   EXTENSION                                 = ".mp4";

    /* Save.tv internal quality/format constants for the API */
    private static final String  API_FORMAT_HD                             = "6";
    private static final String  API_FORMAT_HQ                             = "5";
    private static final String  API_FORMAT_LQ                             = "4";
    /* Save.tv internal quality/format constants for the website */
    private static final String  SITE_FORMAT_HD                            = "2";
    private static final String  SITE_FORMAT_HQ                            = "0";
    private static final String  SITE_FORMAT_LQ                            = "1";

    /* Frequently used internal plugin properties */
    public static final String   PROPERTY_ACCOUNT_API_SESSIONID            = "sessionid";
    private static final String  PROPERTY_DOWNLOADLINK_NORESUME            = "NORESUME";
    private static final String  PROPERTY_DOWNLOADLINK_NOCHUNKS            = "NOCHUNKS";

    @SuppressWarnings("deprecation")
    public SaveTv(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www.save.tv/stv/s/obj/registration/RegPage1.cfm");
        // if (!isJDStable()) {
        setConfigElements();
        // }
    }

    private boolean isJDStable() {
        return System.getProperty("jd.revision.jdownloaderrevision") == null;
    }

    @Override
    public String getAGBLink() {
        return "http://free.save.tv/STV/S/misc/miscShowTermsConditionsInMainFrame.cfm";
    }

    @SuppressWarnings("deprecation")
    @Override
    public void correctDownloadLink(final DownloadLink link) throws Exception {
        link.setUrlDownload(link.getDownloadURL().replaceFirst("http://", "https://"));
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return ACCOUNT_PREMIUM_MAXDOWNLOADS;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return ACCOUNT_PREMIUM_MAXDOWNLOADS;
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    /**
     * TODO: Known Bugs in API mode: API cannot differ between different video formats -> Cannot show any error in case user choice is not
     * available. --> NO FATAL bugs ---> Plugin will work fine with them!
     *
     * @property "category": 0 = undefined, 1 = movies,category: 2 = series, 3 = show, 7 = music
     */

    @SuppressWarnings({ "deprecation", "unused" })
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        br.setFollowRedirects(true);
        link.setProperty("type", EXTENSION);
        /* Show telecast-ID + extension as dummy name for all error cases */
        if (link.getName() != null && (link.getName().contains(getTelecastId(link)) && !link.getName().endsWith(EXTENSION) || link.getName().contains("VideoArchiveDetails.cfm"))) {
            link.setName(getTelecastId(link) + EXTENSION);
        }
        final Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa == null) {
            link.getLinkStatus().setStatusText("Kann Links ohne gültigen Account nicht überprüfen");
            synchronized (LOCK) {
                checkAccountNeededDialog();
            }
            return AvailableStatus.UNCHECKABLE;
        }
        if (this.getPluginConfig().getBooleanProperty(DISABLE_LINKCHECK, false) && !FORCE_LINKCHECK) {
            link.getLinkStatus().setStatusText("Linkcheck deaktiviert - korrekter Dateiname erscheint erst beim Downloadstart");
            return AvailableStatus.TRUE;
        }

        String filesize = null;

        if (is_API_enabled()) {
            API_SESSIONID = api_login(this.br, aa, false);
            /* Last revision with old filename-convert handling: 26988 */
            api_doSoapRequestSafe(this.br, aa, "http://tempuri.org/ITelecast/GetTelecastDetail", "<telecastIds xmlns:a=\"http://schemas.microsoft.com/2003/10/Serialization/Arrays\" xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\"><a:int>" + getTelecastId(link) + "</a:int></telecastIds><detailLevel>2</detailLevel>");
            if (!br.containsHTML("<a:TelecastDetail>")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }

            parseFilenameInformation_api(link, br.toString());
            parseQualityTag(link, null);
        } else {
            site_login(this.br, aa, false);
            final String telecast_ID = getTelecastId(link);
            getPageSafe("https://www.save.tv/STV/M/obj/archive/JSON/VideoArchiveDetailsApi.cfm?TelecastID=" + telecast_ID, aa);
            if (!br.getURL().contains("/JSON/")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            /* Find data of the current telecastID */
            final String data_source = br.getRegex("\"TELECASTDETAILS\":\\{(.+)").getMatch(0);
            if (data_source == null) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            parseFilenameInformation_site(link, data_source);
            parseQualityTag(link, br.toString());
        }
        link.setAvailable(true);
        final String availablecheck_filename = getFilename(link);
        /* Reset (final) filename from previous state so we can use the final filename as final filename later even if it has changed before */
        link.setFinalFileName(null);
        link.setName(null);
        link.setName(availablecheck_filename);

        if (filesize != null) {
            filesize = filesize.replace(".", "");
            final long page_size = SizeFormatter.getSize(filesize.replace(".", ""));
            link.setDownloadSize(page_size);
        } else {
            link.setDownloadSize(calculateFilesize(getLongProperty(link, "site_runtime_minutes", 0)));
        }
        /* TODO: Check if this errormessage still exists */
        if (br.containsHTML(SITE_DL_IMPOSSIBLE)) {
            if (br.containsHTML(SITE_DL_IMPOSSIBLE)) {
                link.getLinkStatus().setStatusText(DL_IMPOSSIBLE_USER_TEXT);
            }
        }
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings("deprecation")
    public static String getFilename(final DownloadLink dl) throws ParseException {
        /*
         * No custom filename if not all required tags are given, if the user prefers original filenames or if custom user regexes for
         * specified series or movies match to force original filenames
         */
        final SubConfiguration cfg = SubConfiguration.getConfig("save.tv");
        final boolean force_original_general = (cfg.getBooleanProperty(USEORIGINALFILENAME) || getLongProperty(dl, "category", 0l) == 0);
        final String site_title = dl.getStringProperty("plainfilename");
        final String server_filename = dl.getStringProperty("server_filename", null);
        final String fake_original_filename = getFakeOriginalFilename(dl);
        boolean force_original_series = false;
        boolean force_original_movies = false;
        String formattedFilename;
        if (isSeries(dl)) {
            formattedFilename = cfg.getStringProperty(CUSTOM_FILENAME_SERIES, defaultCustomSeriesFilename);
            try {
                if (site_title.matches(cfg.getStringProperty(FORCE_ORIGINALFILENAME_SERIES, null))) {
                    force_original_series = true;
                }
            } catch (final Throwable e) {
                System.out.println("FORCE_ORIGINALFILENAME_SERIES custom regex failed");
            }
        } else {
            formattedFilename = cfg.getStringProperty(CUSTOM_FILENAME_MOVIES, defaultCustomFilenameMovies);
            try {
                if (site_title.matches(cfg.getStringProperty(FORCE_ORIGINALFILENAME_MOVIES, null))) {
                    force_original_movies = true;
                }
            } catch (final Throwable e) {
                System.out.println("FORCE_ORIGINALFILENAME_MOVIES custom regex failed");
            }
        }
        /* If user wants to use the original server filename in a custom filename we need to have it present here - if not, we fake it */
        final boolean force_original_original_missing = (formattedFilename.contains("*server_dateiname*") && server_filename == null);
        if (force_original_original_missing) {
            dl.setProperty("server_filename", fake_original_filename.substring(0, fake_original_filename.lastIndexOf(".")));
        }
        final boolean force_original_filename = (force_original_general || force_original_series || force_original_movies);
        String filename;
        if (force_original_filename) {
            filename = fake_original_filename;
        } else {
            filename = getFormattedFilename(dl);
        }
        /* Cut filenames for Windows systems if necessary */
        if (CrossSystem.isWindows() && filename.length() > 255) {
            filename = filename.replace(EXTENSION, "");
            if (filename.length() >= 251) {
                filename = filename.substring(0, 250) + EXTENSION;
            } else {
                filename += EXTENSION;
            }
        }
        return filename;
    }

    public static void parseFilenameInformation_site(final DownloadLink dl, final String source) throws PluginException {
        final String site_title = getJson(source, "STITLE");
        long datemilliseconds = 0;

        /* For series only */
        String episodenumber = getJson(source, "SFOLGE");
        final String episodename = getJson(source, "SSUBTITLE");

        /* General */
        final String genre = getJson(source, "SCHAR");
        final String producecountry = getJson(source, "SCOUNTRY");
        String produceyear = new Regex(source, "\"SPRODUCTIONYEAR\":(\\d+)\\.0").getMatch(0);

        String category = getJson(source, "TVCATEGORYID");
        /* Happens in decrypter - errorhandling! */
        if (category == null && (episodename != null || episodenumber != null)) {
            category = "2";
        } else if (category == null) {
            category = "1";
        }

        final String runtime_start = getJson(source, "DSTARTDATE");
        /* For hosterplugin */
        String runtime_end = getJson(source, "ENDDATE");
        /* For decrypterplugin */
        if (runtime_end == null) {
            runtime_end = getJson(source, "DENDDATE");
        }
        datemilliseconds = TimeFormatter.getMilliSeconds(runtime_start, "yyyy-MM-dd HH:mm:ss", Locale.GERMAN);
        final long site_runtime_minutes = (TimeFormatter.getMilliSeconds(runtime_end, "yyyy-MM-dd HH:mm:ss", Locale.GERMAN) - datemilliseconds) / 1000 / 60;
        final String tv_station = getJson(source, "STVSTATIONNAME");

        /* TODO: Add more/all numbers here, improve this! */
        if (category.equals("2")) {
            /* For series */
            dl.setProperty("category", 2);
        } else if (category.equals("3")) {
            /* For shows */
            dl.setProperty("category", 3);
        } else if (category.equals("7")) {
            /* For music */
            dl.setProperty("category", 7);
        } else if (category.equals("1") || category.equals("6")) {
            /* For movies and magazines */
            dl.setProperty("category", 1);
        } else {
            /* For everything else - same as movie but separated anyways */
            dl.setProperty("category", 1);
            // link.setProperty("category", 0);
        }

        /* Set properties which are needed for filenames */
        /* Add series information */
        if (episodenumber != null) {
            /* json response contains episodenumbers in double-data-type --> Makes no sense --> Correct it here */
            if (episodenumber.matches("^\\d+\\.0$")) {
                episodenumber = new Regex(episodenumber, "^(\\d+)\\.0$").getMatch(0);
            }
            dl.setProperty("episodenumber", correctData(episodenumber));
        }
        if (episodename != null) {
            dl.setProperty("episodename", correctData(episodename));
        }

        /* Add other information */
        if (produceyear != null) {
            dl.setProperty("produceyear", correctData(produceyear));
        }
        if (genre != null) {
            dl.setProperty("genre", correctData(genre));
        }
        if (producecountry != null) {
            dl.setProperty("producecountry", correctData(producecountry));
        }
        dl.setProperty("plain_site_category", category);
        if (tv_station != null) {
            dl.setProperty("plain_tv_station", correctData(tv_station));
        }

        /* Add remaining basic information */
        if (site_title != null) {
            dl.setProperty("plainfilename", correctData(site_title));
        }
        dl.setProperty("originaldate", datemilliseconds);
        dl.setProperty("site_runtime_minutes", site_runtime_minutes);
    }

    public static void parseFilenameInformation_api(final DownloadLink dl, final String source) throws PluginException {
        final String site_title = new Regex(source, "<a:T>([^<>]*?)</a:T>").getMatch(0);
        long datemilliseconds = 0;

        /* For series only */
        final String episodenumber = new Regex(source, "<a:Episode>([^<>\"]*?)</a:Episode>").getMatch(0);
        final String episodename = new Regex(source, "<a:ST>([^<>\"]*?)</a:ST>").getMatch(0);

        /* General */
        final String genre = new Regex(source, "<a:Char>([^<>\"]*?)</a:Char>").getMatch(0);

        String category = new Regex(source, "global/TVCategorie/kat(\\d+)\\.jpg</a:MIP>").getMatch(0);
        /* Happens in decrypter - errorhandling! */
        if (category == null && (episodename != null || episodenumber != null)) {
            category = "2";
        } else if (category == null) {
            category = "1";
        }

        String runtime_start = new Regex(source, "<a:SD>([^<>\"]*?)</a:SD>").getMatch(0);
        /* For hosterplugin */
        String runtime_end = new Regex(source, "<a:EndDate>([^<>\"]*?)</a:EndDate>").getMatch(0);
        runtime_start = runtime_start.replace("T", " ");
        runtime_end = runtime_end.replace("T", " ");
        datemilliseconds = TimeFormatter.getMilliSeconds(runtime_start, "yyyy-MM-dd HH:mm:ss", Locale.GERMAN);
        final long site_runtime_minutes = (TimeFormatter.getMilliSeconds(runtime_end, "yyyy-MM-dd HH:mm:ss", Locale.GERMAN) - datemilliseconds) / 1000 / 60;
        final String tv_station = getJson(source, "STVSTATIONNAME");

        /* TODO: Add more/all numbers here, improve this! */
        if (category.equals("2")) {
            /* For series */
            dl.setProperty("category", 2);
        } else if (category.equals("3")) {
            /* For shows */
            dl.setProperty("category", 3);
        } else if (category.equals("7")) {
            /* For music */
            dl.setProperty("category", 7);
        } else if (category.equals("1") || category.equals("6")) {
            /* For movies and magazines */
            dl.setProperty("category", 1);
        } else {
            /* For everything else - same as movie but separated anyways */
            dl.setProperty("category", 1);
            // link.setProperty("category", 0);
        }

        /* Set properties which are needed for filenames */
        /* Add series information */
        if (episodenumber != null) {
            dl.setProperty("episodenumber", correctData(episodenumber));
        }
        if (episodename != null) {
            dl.setProperty("episodename", correctData(episodename));
        }

        /* Add other information */
        if (genre != null) {
            dl.setProperty("genre", correctData(genre));
        }
        dl.setProperty("plain_site_category", category);
        if (tv_station != null) {
            dl.setProperty("plain_tv_station", correctData(tv_station));
        }

        /* Add remaining basic information */
        if (site_title != null) {
            dl.setProperty("plainfilename", correctData(site_title));
        }
        dl.setProperty("originaldate", datemilliseconds);
        dl.setProperty("site_runtime_minutes", site_runtime_minutes);
    }

    public static void parseQualityTag(final DownloadLink dl, final String source) {
        final int selected_video_format = getConfiguredVideoFormat();
        /*
         * If we have no source, we can select HQ if the user chose HQ because it is always available. If the user selects any other quality
         * we need to know whether it exists or not and then set the data.
         */
        if (source == null) {
            if (selected_video_format == 1) {
                dl.setProperty(QUALITY_PARAM, QUALITY_HQ);
            }
        } else {
            final String availableformatstext = new Regex(source, "\"ARRALLOWDDOWNLOADFORMATS\":\\[(.*?)\\]").getMatch(0);
            // final String[] availableformats_array = availableformatstext.split("\\},\\{");
            switch (selected_video_format) {
            case 0:
                if (availableformatstext.contains("\"RECORDINGFORMATID\":6.0")) {
                    dl.setProperty(QUALITY_PARAM, QUALITY_HD);
                } else {
                    dl.setProperty(QUALITY_PARAM, QUALITY_HQ);
                }
                break;
            case 1:
                dl.setProperty(QUALITY_PARAM, QUALITY_HQ);
                break;
            case 2:
                if (availableformatstext.contains("\"RECORDINGFORMATID\":4.0")) {
                    dl.setProperty(QUALITY_PARAM, QUALITY_LQ);
                } else {
                    dl.setProperty(QUALITY_PARAM, QUALITY_HQ);
                }
                break;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        final Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa == null) {
            try {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            } catch (final Throwable e) {
                if (e instanceof PluginException) {
                    throw (PluginException) e;
                }
            }
            throw new PluginException(LinkStatus.ERROR_FATAL, "Only downloadable for premium users");
        }
        // Bad workaround for bug: http://svn.jdownloader.org/issues/10306
        logger.warning("Downloading as premium in free mode as a workaround for bug #10306");
        try {
            handlePremium(downloadLink, aa);
        } catch (final PluginException e) {
            /* Catch premium errors - usually the account would be deactivated then -> Wait */
            if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "FATAL server error", 30 * 60 * 1000l);
            }
            /* Show other download errors */
            throw e;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink downloadLink, final Account account) throws Exception {
        synchronized (LOCK) {
            checkFeatureDialogAll();
            checkFeatureDialogCrawler();
        }
        final SubConfiguration cfg = SubConfiguration.getConfig("save.tv");
        final boolean preferAdsFree = cfg.getBooleanProperty(PREFERADSFREE, false);
        String downloadWithoutAds_request_value = Boolean.toString(preferAdsFree);
        FORCE_LINKCHECK = true;
        requestFileInformation(downloadLink);

        if (apiActive()) {
            /* Check if ads-free version is available */
            api_doSoapRequestSafe(this.br, account, "http://tempuri.org/IVideoArchive/GetAdFreeState", "<telecastId i:type=\"d:int\">" + getTelecastId(downloadLink) + "</telecastId><telecastIdSpecified i:type=\"d:boolean\">true</telecastIdSpecified>");
            if (br.containsHTML("<a:IsAdFreeAvailable>false</a:IsAdFreeAvailable>")) {
                downloadLink.getLinkStatus().setStatusText(ADSFREEANOTVAILABLE);
                ISADSFREEAVAILABLE = false;
            } else {
                downloadLink.getLinkStatus().setStatusText(ADSFREEAVAILABLETEXT);
                ISADSFREEAVAILABLE = true;
            }
        } else {
            /* TODO: Check if this errormessage still exists */
            if (br.containsHTML(SITE_DL_IMPOSSIBLE)) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, DL_IMPOSSIBLE_USER_TEXT, 30 * 60 * 1000l);
            }
            /* Check if ads-free version is available */
            /* TODO: Check if the numbers are still correct */
            /* TODO: Enhance ad-free check - check if selected format is available and if it is available in ad-free */
            final String ad_Free_availability = getJson(br.toString(), "BADFREEAVAILABLE");
            if (ad_Free_availability.equals("3")) {
                downloadLink.getLinkStatus().setStatusText(JDL.L("plugins.hoster.SaveTv.NoCutListAvailable", NOCUTAVAILABLETEXT));
            } else if (ad_Free_availability.equals("1")) {
                downloadLink.getLinkStatus().setStatusText(ADSFREEAVAILABLETEXT);
                ISADSFREEAVAILABLE = true;
            } else {
                /* ad_Free_availability == "2" */
                downloadLink.getLinkStatus().setStatusText(ADSFREEANOTVAILABLE);
                ISADSFREEAVAILABLE = false;
            }
        }

        String dllink = null;
        /*
         * User wants ads-free but it's not available -> Wait X [User-Defined DOWNLOADONLYADSFREE_RETRY_HOURS] hours, status can still
         * change but probably won't -> If defined by user, force version with ads after a user defined amount of retries.
         */
        if (preferAdsFree && !this.ISADSFREEAVAILABLE) {
            logger.info("Ad-free version is unavailable");
            final long maxRetries = getLongProperty(cfg, ADS_FREE_UNAVAILABLE_MAXRETRIES, defaultADS_FREE_UNAVAILABLE_MAXRETRIES);
            long currentTryCount = getLongProperty(downloadLink, "curren_no_ads_free_available_retries", 0);
            final boolean load_with_ads = (maxRetries != 0 && currentTryCount >= maxRetries);

            if (!load_with_ads) {
                logger.info("Ad-free version is unavailable --> Waiting");
                currentTryCount++;
                downloadLink.setProperty("curren_no_ads_free_available_retries", currentTryCount);
                logger.info("--> Throw Exception_no_ads_free_1 | Try " + currentTryCount + "/" + maxRetries);
                final long userDefinedWaitHours = getLongProperty(cfg, ADS_FREE_UNAVAILABLE_HOURS, SaveTv.defaultADS_FREE_UNAVAILABLE_HOURS);
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, NOCUTAVAILABLETEXT, userDefinedWaitHours * 60 * 60 * 1000l);
            } else {
                logger.info("Ad-free version is unavailable --> Downloading version with ads");
                downloadWithoutAds_request_value = "false";
            }
        }

        /* Set download options (ads-free or with ads) and get download url */
        String stv_request_selected_format_value = null;
        if (apiActive()) {
            stv_request_selected_format_value = api_get_format_request_value();
            api_postDownloadPage(downloadLink, stv_request_selected_format_value, downloadWithoutAds_request_value);
            if (br.containsHTML(API_DL_IMPOSSIBLE)) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, DL_IMPOSSIBLE_USER_TEXT, 30 * 60 * 1000l);
            }
            dllink = br.getRegex("<a:DownloadUrl>(http://[^<>\"]*?)</a").getMatch(0);
        } else {
            stv_request_selected_format_value = site_get_format_request_value();
            site_GetDownloadPage(downloadLink, stv_request_selected_format_value, downloadWithoutAds_request_value);
            /* TODO: Check if their new system still has this errormessage */
            if (br.containsHTML("Die Aufnahme liegt nicht im gewünschten Format vor")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, PREFERREDFORMATNOTAVAILABLETEXT, 4 * 60 * 60 * 1000l);
            }
            /* Ads-Free version not available - handle it */
            if (br.containsHTML("\"Leider enthält Ihre Aufnahme nur Werbung")) {
                logger.info("Received server error 'Leider enthält Ihre Aufnahme nur Werbung'");
                final long maxRetries = getLongProperty(cfg, FORCE_WITH_ADS_ON_ERROR_MAXRETRIES, defaultFORCE_WITH_ADS_ON_ERROR_maxRetries);
                final long waitHours = getLongProperty(cfg, FORCE_WITH_ADS_ON_ERROR_HOURS, defaultFORCE_WITH_ADS_ON_ERROR_HOURS);
                long currentTryCount = getLongProperty(downloadLink, "curren_no_hd_ads_free_available_retries", 0);
                final boolean load_with_ads = (maxRetries != 0 && currentTryCount >= maxRetries);
                if (!load_with_ads) {
                    currentTryCount++;
                    downloadLink.setProperty("curren_no_hd_ads_free_available_retries", currentTryCount);
                    logger.info("--> Throw Exception_no_ads_free_2 | Try " + currentTryCount + "/" + maxRetries);
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Versuch " + currentTryCount + "/" + maxRetries + "Serverfehler: 'Leider enthält Ihre Aufnahme nur Werbung' (HD Format gewählt aber nicht verfügbar)", waitHours * 60 * 60 * 1000l);
                } else {
                    logger.info("--> Max retries reached: " + maxRetries + " --> Trying to download adsfree version");
                    site_GetDownloadPage(downloadLink, stv_request_selected_format_value, "false");
                    if (br.containsHTML("\"Leider enthält Ihre Aufnahme nur Werbung")) {
                        throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Serverfehler: 'Leider enthält Ihre Aufnahme nur Werbung' (Ausweichen auf HQ Format hat nichts gebracht)", 12 * 60 * 60 * 1000l);
                    }
                }
                if (br.containsHTML("\"NOK\",\"unknown error\"")) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Serverfehler - finaler Downloadlink wurde nicht zurückgegeben: '\"NOK\",\"unknown error\"'", 15 * 60 * 1000l);
                }
            }
            dllink = br.getRegex("(\\'|\")(http://[^<>\"\\']+/\\?m=dl)(\\'|\")").getMatch(1);
        }
        if (dllink == null) {
            logger.warning("Final downloadlink (dllink) is null");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        logger.info("Final downloadlink = " + dllink + " starting download...");
        int maxChunks = ACCOUNT_PREMIUM_MAXCHUNKS;
        boolean resume = ACCOUNT_PREMIUM_RESUME;
        if (downloadLink.getBooleanProperty(PROPERTY_DOWNLOADLINK_NORESUME, false)) {
            resume = false;
        }
        if (downloadLink.getBooleanProperty(SaveTv.PROPERTY_DOWNLOADLINK_NOCHUNKS, false) || resume == false) {
            maxChunks = 1;
        }

        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, resume, maxChunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Serverfehler 404", 60 * 60 * 1000l);
            }
            /* Handle (known) errors */
            logger.warning("Save.tv: Received HTML code instead of the file!");
            br.followConnection();
            if (br.containsHTML(">Die Aufnahme kann zum aktuellen Zeitpunkt nicht vollständig heruntergeladen werden")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Serverfehler: 'Die Aufnahme kann zum aktuellen Zeitpunkt nicht vollständig heruntergeladen werden'");
            }

            /* Handle unknown errors */
            logger.info(NICE_HOST + ": timesfailed_unknown_dlerror");
            int timesFailed = downloadLink.getIntegerProperty(NICE_HOSTproperty + "timesfailed_unknown_dlerror", 0);
            downloadLink.getLinkStatus().setRetryCount(0);
            if (timesFailed <= 2) {
                timesFailed++;
                downloadLink.setProperty(NICE_HOSTproperty + "timesfailed_unknown_dlerror", timesFailed);
                logger.info(NICE_HOST + ": timesfailed_unknown_dlerror -> Retrying");
                throw new PluginException(LinkStatus.ERROR_RETRY, "timesfailed_unknown_dlerror");
            } else {
                downloadLink.setProperty(NICE_HOSTproperty + "timesfailed_unknown_dlerror", Property.NULL);
                logger.info(NICE_HOST + ": timesfailed_unknown_dlerror - disabling current host!");
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unbekannter Serverfehler 1 - bitte dem JDownloader Support mit Log melden!", 60 * 60 * 1000l);
            }
        } else if (dl.getConnection().getLongContentLength() <= 1048576) {
            /* Avoid downloading (too small) trash data */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Serverfehler: Datei vom Server zu klein", 60 * 60 * 1000l);
        }
        String server_filename = getFileNameFromHeader(dl.getConnection());
        server_filename = fixCharIssues(server_filename);
        server_filename = server_filename.substring(0, server_filename.lastIndexOf("."));
        downloadLink.setProperty("server_filename", server_filename);
        try {
            /* This is for checking server speed. */
            final String previouscomment = downloadLink.getComment();
            if (previouscomment == null || previouscomment.contains("Aktuell/Zuletzt verwendeter direkter Downloadlink:")) {
                downloadLink.setComment("Aktuell/Zuletzt verwendeter direkter Downloadlink: " + dllink);
            }
        } catch (final Throwable e) {
            /* Not available in old 0.9.581 Stable */
        }

        final String final_filename = getFilename(downloadLink);
        downloadLink.setFinalFileName(final_filename);
        try {
            if (!this.dl.startDownload()) {
                try {
                    if (dl.externalDownloadStop()) {
                        return;
                    }
                } catch (final Throwable e) {
                }
                /* Unknown error, we disable multiple chunks */
                if (downloadLink.getLinkStatus().getErrorMessage() != null && downloadLink.getBooleanProperty(SaveTv.PROPERTY_DOWNLOADLINK_NOCHUNKS, false) == false) {
                    downloadLink.setProperty(SaveTv.PROPERTY_DOWNLOADLINK_NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            } else {
                if (cfg.getBooleanProperty(DELETE_TELECAST_ID_AFTER_DOWNLOAD, defaultDeleteTelecastIDAfterDownload)) {
                    logger.info("Download finished --> User WANTS telecastID " + getTelecastId(downloadLink) + " deleted");
                    site_killTelecastID(account, downloadLink);
                } else {
                    logger.info("Download finished --> User does NOT want telecastID " + getTelecastId(downloadLink) + " deleted");
                }
            }
        } catch (final PluginException e) {
            if (e.getLinkStatus() == LinkStatus.ERROR_DOWNLOAD_INCOMPLETE) {
                logger.info("ERROR_DOWNLOAD_INCOMPLETE --> Handling it");
                if (downloadLink.getBooleanProperty(PROPERTY_DOWNLOADLINK_NORESUME, false)) {
                    downloadLink.setProperty(PROPERTY_DOWNLOADLINK_NORESUME, Boolean.valueOf(false));
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unbekannter Serverfehler 2", 30 * 60 * 1000l);
                }
                downloadLink.setProperty(PROPERTY_DOWNLOADLINK_NORESUME, Boolean.valueOf(true));
                downloadLink.setChunksProgress(null);
                throw new PluginException(LinkStatus.ERROR_RETRY, "ERROR_DOWNLOAD_INCOMPLETE --> Retrying");
            }
            try {
                if (e.getLinkStatus() == LinkStatus.ERROR_ALREADYEXISTS) {
                    if (cfg.getBooleanProperty(DELETE_TELECAST_ID_IF_FILE_ALREADY_EXISTS, defaultDeleteTelecastIDIfFileAlreadyExists)) {
                        logger.info("ERROR_ALREADYEXISTS --> User WANTS telecastID " + getTelecastId(downloadLink) + " deleted");
                        site_killTelecastID(account, downloadLink);
                    } else {
                        logger.info("ERROR_ALREADYEXISTS --> User does NOT want telecastID " + getTelecastId(downloadLink) + " deleted");
                    }
                    throw e;
                }
            } catch (final Throwable efail) {
                /* Do not fail here, throw exception which happened previously */
                throw e;
            }
            /* New V2 errorhandling */
            /* unknown error, we disable multiple chunks */
            if (e.getLinkStatus() != LinkStatus.ERROR_RETRY && downloadLink.getBooleanProperty(SaveTv.PROPERTY_DOWNLOADLINK_NOCHUNKS, false) == false) {
                downloadLink.setProperty(SaveTv.PROPERTY_DOWNLOADLINK_NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            throw e;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        loginSafe(this.br, account, true);
        /* We're logged in - don't fail here! */
        String acctype = null;
        try {
            if (is_API_enabled()) {
                // doSoapRequest("http://tempuri.org/IUser/GetUserStatus", "<sessionId i:type=\"d:string\">" + SESSIONID + "</sessionId>");
                acctype = "XL Account";
            } else {
                /* Get long lasting login cookie */
                String long_cookie = br.getCookie("http://save.tv/", "SLOCO");
                if (long_cookie == null || long_cookie.trim().equals("bAutoLoginActive=1")) {
                    logger.info("Long session cookie does not exist yet/anymore - enabling it");
                    br.postPage("https://www.save.tv/STV/M/obj/user/submit/submitAutoLogin.cfm", "IsAutoLogin=true&Messages=");
                    long_cookie = br.getCookie("http://save.tv/", "SLOCO");
                    if (long_cookie == null || long_cookie.trim().equals("")) {
                        logger.info("Failed to get long session cookie");
                    } else {
                        logger.info("Successfully received long session cookie and saved cookies");
                        saveCookies(br, account);
                    }
                } else {
                    logger.info("Long session cookie exists");
                }
                /* Find account details */
                String price = null;
                br.getPage("https://www.save.tv/STV/M/obj/user/JSON/userConfigApi.cfm?iFunction=2");
                final String acc_username = br.getRegex("\"SUSERNAME\":(\\d+)").getMatch(0);
                final String user_packet_id = getJson(br.toString(), "CURRENTARTICLEID");
                /* Find the price of the package which the user uses. */
                final String all_packages_string = br.getRegex("\"ARRRENEWARTICLES\":\\[(.*?)\\]").getMatch(0);
                final String[] all_packets = all_packages_string.split("\\},\\{");
                for (final String packet : all_packets) {
                    if (packet.contains("\"ID\":" + user_packet_id + ".0")) {
                        price = getJson(packet, "IPRICE");
                    }
                }

                final String expireDate = getJson(br.toString(), "DCURRENTARTICLEENDDATE");
                ai.setValidUntil(TimeFormatter.getMilliSeconds(expireDate, "yyyy-MM-dd hh:mm:ss", Locale.GERMAN));
                account.setProperty("acc_expire", expireDate);
                final String package_name = getJson(br.toString(), "SCURRENTARTICLENAME");
                if (package_name.contains("Basis")) {
                    acctype = "Basis Account";
                } else {
                    acctype = "XL Account";
                }
                final String runtime = new Regex(package_name, "(\\d+ Monate)").getMatch(0);
                account.setProperty("acc_package", correctData(package_name));
                if (price != null) {
                    account.setProperty("acc_price", correctData(price));
                }
                if (runtime != null) {
                    account.setProperty("acc_runtime", correctData(runtime));
                }
                if (acc_username != null) {
                    this.getPluginConfig().setProperty("acc_username", correctData(acc_username));
                }

                br.getPage("https://www.save.tv/STV/M/obj/archive/JSON/VideoArchiveApi.cfm?iEntriesPerPage=1");
                final String totalLinks = getJson(br.toString(), "ITOTALENTRIES");
                if (totalLinks != null) {
                    account.setProperty("acc_count_telecast_ids", totalLinks);
                }
            }
            try {
                account.setType(AccountType.PREMIUM);
            } catch (final Throwable e) {
                account.setProperty("free", false);
            }
        } catch (final Throwable e) {
            /* Should not happen but it won't hurt */
            logger.info("Extended account check failed");
        }
        ai.setStatus(acctype);
        account.setProperty("acc_type", acctype);
        ai.setUnlimitedTraffic();
        account.setValid(true);
        return ai;
    }

    /** Performs login respecting api setting */
    private void login(final Browser br, final Account account, final boolean force) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        if (is_API_enabled()) {
            API_SESSIONID = api_login(br, account, force);
        } else {
            site_login(br, account, force);
        }
    }

    @SuppressWarnings("unchecked")
    public static void site_login(final Browser br, final Account account, final boolean force) throws IOException, PluginException {
        final String lang = System.getProperty("user.language");
        site_prepBrowser(br);
        synchronized (LOCK) {
            try {
                /* Load cookies */
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
                            br.setCookie(COOKIE_HOST, key, value);
                        }
                        return;
                    }
                }
                final String postData = "sUsername=" + Encoding.urlEncode(account.getUser()) + "&sPassword=" + Encoding.urlEncode(account.getPass()) + "&bAutoLoginActivate=1";
                br.postPage("https://www.save.tv/STV/M/Index.cfm?sk=PREMIUM", postData);
                if (br.containsHTML("No htmlCode read")) {
                    br.getPage("https://www.save.tv/STV/M/obj/TVProgCtr/tvctShow.cfm");
                }
                if (!br.containsHTML("class=\"member\\-nav\\-li member\\-nav\\-account \"") || br.containsHTML("Bitte verifizieren Sie Ihre Logindaten")) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                final String acc_count_archive_entries = br.getRegex(">(\\d+) Sendungen im Archiv<").getMatch(0);
                if (acc_count_archive_entries != null) {
                    account.setProperty("acc_count_archive_entries", acc_count_archive_entries);
                }
                /* Save cookies & account data */
                saveCookies(br, account);
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }

    }

    public static String api_login(final Browser br, final Account account, final boolean force) throws IOException, PluginException {
        final String lang = System.getProperty("user.language");
        String api_sessionid = account.getStringProperty(PROPERTY_ACCOUNT_API_SESSIONID, null);
        final long lastUse = getLongProperty(account, "lastuse", -1l);
        /* Only generate new sessionID if we have none or it's older than 6 hours */
        if (api_sessionid == null || (System.currentTimeMillis() - lastUse) > 360000 || force) {
            api_prepBrowser(br);
            api_doSoapRequest(br, "http://tempuri.org/ISession/CreateSession", "<apiKey>" + Encoding.Base64Decode(APIKEY) + "</apiKey>");
            api_sessionid = br.getRegex("<a:SessionId>([^<>\"]*?)</a:SessionId>").getMatch(0);
            if (api_sessionid == null) {
                if ("de".equalsIgnoreCase(lang)) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            account.setProperty("lastuse", System.currentTimeMillis());
            account.setProperty(PROPERTY_ACCOUNT_API_SESSIONID, api_sessionid);
        }
        api_doSoapRequest(br, "http://tempuri.org/IUser/Login", "<sessionId>" + api_sessionid + "</sessionId><username>" + account.getUser() + "</username><password>" + account.getPass() + "</password>");
        if (!br.containsHTML("<a:HasPremiumStatus>true</a:HasPremiumStatus>")) {
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        return api_sessionid;
    }

    /**
     * Log in, handle all kinds of timeout- and browser errors - as long as we know that the account is valid it should stay active!
     */
    @SuppressWarnings("deprecation")
    private void loginSafe(final Browser br, final Account acc, final boolean force) throws Exception {
        boolean success = false;
        try {
            for (int i = 1; i <= MAX_RETRIES_LOGIN; i++) {
                logger.info("Login try " + i + " of " + MAX_RETRIES_LOGIN);
                try {
                    login(br, acc, true);
                } catch (final Exception e) {
                    if (e instanceof UnknownHostException || e instanceof BrowserException) {
                        logger.info("Login " + i + " of " + MAX_RETRIES_LOGIN + " failed because of server error or timeout");
                        continue;
                    } else {
                        throw e;
                    }
                }
                logger.info("Login " + i + " of " + MAX_RETRIES_LOGIN + " was successful");
                success = true;
                break;
            }
        } catch (final PluginException ep) {
            logger.info("save.tv Account ist ungültig!");
            acc.setValid(false);
            throw ep;
        }
        if (!success) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nLogin wegen Serverfehler oder Timeout fehlgeschlagen!", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nLogin failed because of server error or timeout!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
    }

    /* Always compare to the decrypter */
    private void getPageSafe(final String url, final Account acc) throws Exception {
        /*
         * Max 30 logins possible Max 3 accesses of the link possible -> Max 33 total requests
         */
        for (int i = 1; i <= MAX_RETRIES_SAFE_REQUEST; i++) {
            getPageCorrectBr(this.br, url);
            if (br.getURL().contains("Token=MSG_LOGOUT_B")) {
                logger.info("Refreshing cookies to continue downloading " + i + " of " + MAX_RETRIES_SAFE_REQUEST);
                br.clearCookies(COOKIE_HOST);
                loginSafe(br, acc, true);
                continue;
            }
            if (i > 1) {
                logger.info("Successfully refreshed cookies to access url: " + url);
            }
            break;
        }
    }

    /* Avoid 503 server errors */
    @SuppressWarnings("unused")
    private void postPageSafe(final Browser br, final String url, final String postData) throws IOException, PluginException, InterruptedException {
        for (int i = 1; i <= 3; i++) {
            try {
                br.postPage(url, postData);
            } catch (final BrowserException e) {
                if (br.getRequest().getHttpConnection().getResponseCode() == 503) {
                    logger.info("503 BrowserException occured, retry " + i + " of 3");
                    Thread.sleep(3000);
                    continue;
                }
                logger.info("Unhandled BrowserException occured...");
                throw e;
            }
            break;
        }
    }

    public static void getPageCorrectBr(final Browser br, final String url) throws IOException {
        br.getPage(url);
        br.getRequest().setHtmlCode(br.toString().replace("\\", ""));
    }

    /**
     * Tries to return value of key from JSon response, from String source.
     *
     * @author raztoki
     * */
    private static String getJson(final String source, final String key) {
        String result = new Regex(source, "\"" + key + "\":(-?\\d+(\\.\\d+)?|true|false|null)").getMatch(0);
        if (resultIsEmpty(source, key)) {
            return null;
        }
        if (result == null) {
            result = new Regex(source, "\"" + key + "\":\"([^\"]*?)\"").getMatch(0);
        }
        if (result == null || result.equals("")) {
            /* Workaround - sometimes they use " plain in json even though usually this has to be encoded! */
            result = new Regex(source, "\"" + key + "\":\"([^<>]*?)\",\"").getMatch(0);
        }
        if (result != null) {
            result = result.replaceAll("\\\\/", "/");
        }
        return result;
    }

    private static boolean resultIsEmpty(final String source, final String key) {
        return source.matches(".+\"" + key + "\":\"\"(,|\\}).+");
    }

    /**
     * @param dl
     *            DownloadLink
     * @param user_selected_video_quality
     *            : Vom Benutzer bevorzugte Qualitätsstufe
     * @param downloadWithoutAds
     *            : Videos mit angewandter Schnittliste bevorzugen oder nicht
     */
    @SuppressWarnings("unused")
    private void api_PostDownloadPage(final DownloadLink dl, final String user_selected_video_quality, final String downloadWithoutAds) throws IOException {
        br.postPage("https://www.save.tv/STV/M/obj/cRecordOrder/croGetDownloadUrl.cfm?null.GetDownloadUrl", "ajax=true&clientAuthenticationKey=&callCount=1&c0-scriptName=null&c0-methodName=GetDownloadUrl&c0-id=&c0-param0=number:" + getTelecastId(dl) + "&" + user_selected_video_quality + "&c0-param2=boolean:" + downloadWithoutAds + "&xml=true&");
    }

    /**
     * @param dl
     *            DownloadLink
     * @param user_selected_video_quality
     *            : Vom Benutzer bevorzugte Qualitätsstufe
     * @param downloadWithoutAds
     *            : Videos mit angewandter Schnittliste bevorzugen oder nicht
     */
    private void site_GetDownloadPage(final DownloadLink dl, final String user_selected_video_quality, final String downloadWithoutAds) throws IOException {
        getPageCorrectBr(this.br, "https://www.save.tv/STV/M/obj/cRecordOrder/croGetDownloadUrl.cfm?TelecastId=" + getTelecastId(dl) + "&iFormat=" + user_selected_video_quality + "&bAdFree=" + downloadWithoutAds);
    }

    /**
     * @param dl
     *            DownloadLink
     * @param user_selected_video_quality
     *            : MVom Benutzer bevorzugte Qualitätsstufe
     * @param downloadWithoutAds
     *            : Videos mit angewandter Schnittliste bevorzugen oder nicht
     */
    private void api_postDownloadPage(final DownloadLink dl, final String user_selected_video_quality, final String downloadWithoutAds) throws IOException {
        api_doSoapRequest(this.br, "http://tempuri.org/IDownload/GetStreamingUrl", "<sessionId i:type=\"d:string\">" + API_SESSIONID + "</sessionId><telecastId i:type=\"d:int\">" + getTelecastId(dl) + "</telecastId><telecastIdSpecified i:type=\"d:boolean\">true</telecastIdSpecified><recordingFormatId i:type=\"d:int\">" + user_selected_video_quality + "</recordingFormatId><recordingFormatIdSpecified i:type=\"d:boolean\">true</recordingFormatIdSpecified><adFree i:type=\"d:boolean\">false</adFree><adFreeSpecified i:type=\"d:boolean\">" + downloadWithoutAds + "</adFreeSpecified>");
    }

    /**
     * Deletes a desired telecastID.
     *
     * @param acc
     *            Account : The users' account which might be needed to log in in case the user uses the API
     * @param dl
     *            DownloadLink: The DownloadLink whose telecastID will be deleted.
     */
    private void site_killTelecastID(final Account acc, final DownloadLink dl) throws IOException, PluginException {
        try {
            /* If the API is used, we need to log in via site here to be able to delete the telecastID */
            if (apiActive()) {
                site_login(this.br, acc, false);
            }
            br.getPage("https://www.save.tv/STV/M/obj/cRecordOrder/croDelete.cfm?TelecastID=" + getTelecastId(dl));
            if (br.containsHTML("\"ok\"")) {
                logger.info("Successfully deleted telecastID: " + getTelecastId(dl));
            } else {
                logger.warning("Failed to delete telecastID: " + getTelecastId(dl));
            }
        } catch (final Throwable e) {
            logger.info("Failed to delete telecastID: " + getTelecastId(dl));
        }
    }

    private static void site_prepBrowser(final Browser br) {
        br.setReadTimeout(3 * 60 * 1000);
        br.setConnectTimeout(3 * 60 * 1000);
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:27.0) Gecko/20100101 Firefox/27.0");
    }

    private static void api_prepBrowser(final Browser br) {
        br.setReadTimeout(3 * 60 * 1000);
        br.setConnectTimeout(3 * 60 * 1000);
        br.getHeaders().put("User-Agent", "kSOAP/2.0");
        br.getHeaders().put("Content-Type", "text/xml");
    }

    private boolean apiActive() {
        return (API_SESSIONID != null && is_API_enabled());
    }

    public static long calculateFilesize(final String minutes) {
        double calculated_filesize = 0;
        final long duration_minutes = Long.parseLong(minutes);
        final int user_format = getConfiguredVideoFormat();
        switch (user_format) {
        case 0:
            calculated_filesize = QUALITY_HD_MB_PER_MINUTE * duration_minutes * 1024 * 1024;
            break;
        case 1:
            calculated_filesize = QUALITY_H264_NORMAL_MB_PER_MINUTE * duration_minutes * 1024 * 1024;
            break;
        case 2:
            calculated_filesize = QUALITY_H264_MOBILE_MB_PER_MINUTE * duration_minutes * 1024 * 1024;
            break;
        }
        return (long) calculated_filesize;
    }

    public static long calculateFilesize(final long minutes) {
        double calculated_filesize = 0;
        final long duration_minutes = minutes;
        final int user_format = getConfiguredVideoFormat();
        switch (user_format) {
        case 0:
            calculated_filesize = QUALITY_HD_MB_PER_MINUTE * duration_minutes * 1024 * 1024;
            break;
        case 1:
            calculated_filesize = QUALITY_H264_NORMAL_MB_PER_MINUTE * duration_minutes * 1024 * 1024;
            break;
        case 2:
            calculated_filesize = QUALITY_H264_MOBILE_MB_PER_MINUTE * duration_minutes * 1024 * 1024;
            break;
        }
        return (long) calculated_filesize;
    }

    /**
     * Returns the format value needed for format specific requests TODO: Once serverside implemented, add support for HD - at the moment,
     * if user selected HD, Normal H.264 will be downloaded instead
     */
    private String api_get_format_request_value() {
        final int selected_video_format = getConfiguredVideoFormat();
        String stv_request_selected_format = null;
        switch (selected_video_format) {
        case 0:
            stv_request_selected_format = API_FORMAT_HD;
            break;
        case 1:
            stv_request_selected_format = API_FORMAT_HQ;
            break;
        case 2:
            stv_request_selected_format = API_FORMAT_LQ;
            break;
        }
        return stv_request_selected_format;
    }

    /** Returns the format value needed for format specific requests */
    private String site_get_format_request_value() {
        final int selected_video_format = getConfiguredVideoFormat();
        String stv_request_selected_format = null;
        switch (selected_video_format) {
        case 0:
            stv_request_selected_format = SITE_FORMAT_HD;
            break;
        case 1:
            stv_request_selected_format = SITE_FORMAT_HQ;
            break;
        case 2:
            stv_request_selected_format = SITE_FORMAT_LQ;
            break;
        }
        return stv_request_selected_format;
    }

    @SuppressWarnings("deprecation")
    public static int getConfiguredVideoFormat() {
        switch (SubConfiguration.getConfig("save.tv").getIntegerProperty(selected_video_format, -1)) {
        case 0:
            return 0;
        case 1:
            return 1;
        case 2:
            return 2;
        default:
            return 0;
        }
    }

    @SuppressWarnings("unused")
    private double site_get_calculated_runtime_minutes(final long page_size_mb) {
        double run_time_calculated = 0;
        final int selected_video_format = getConfiguredVideoFormat();
        switch (selected_video_format) {
        case 0:
            run_time_calculated = page_size_mb / QUALITY_HD_MB_PER_MINUTE;
            break;
        case 1:
            run_time_calculated = page_size_mb / QUALITY_H264_NORMAL_MB_PER_MINUTE;
            break;
        case 2:
            run_time_calculated = page_size_mb / QUALITY_H264_MOBILE_MB_PER_MINUTE;
            break;
        }
        return run_time_calculated;
    }

    /**
     * Performs save.tv API soap requests.
     *
     * @param soapAction
     *            : The soap link which should be accessed
     * @param soapPost
     *            : The soap post data
     */
    private static void api_doSoapRequest(final Browser br, final String soapAction, final String soapPost) throws IOException {
        final String method = new Regex(soapAction, "([A-Za-z0-9]+)$").getMatch(0);
        br.getHeaders().put("SOAPAction", soapAction);
        br.getHeaders().put("Content-Type", "text/xml");
        final String postdata = "<?xml version=\"1.0\" encoding=\"utf-8\"?><v:Envelope xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:d=\"http://www.w3.org/2001/XMLSchema\" xmlns:c=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:v=\"http://schemas.xmlsoap.org/soap/envelope/\"><v:Header /><v:Body><" + method + " xmlns=\"http://tempuri.org/\" id=\"o0\" c:root=\"1\">" + soapPost + "</" + method + "></v:Body></v:Envelope>";
        br.postPageRaw("http://api.save.tv/v2/Api.svc", postdata);
    }

    /**
     * Performs save.tv API soap requests. If the session-id expires it will get refreshed.
     *
     * @param soapAction
     *            : The soap link which should be accessed
     * @param soapPost
     *            : The soap post data
     * @throws PluginException
     */
    public static void api_doSoapRequestSafe(final Browser br, final Account acc, final String soapAction, final String soapPost) throws IOException, PluginException {
        final String method = new Regex(soapAction, "([A-Za-z0-9]+)$").getMatch(0);
        br.getHeaders().put("SOAPAction", soapAction);
        br.getHeaders().put("Content-Type", "text/xml");
        String postdata = "<?xml version=\"1.0\" encoding=\"utf-8\"?><v:Envelope xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:d=\"http://www.w3.org/2001/XMLSchema\" xmlns:c=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:v=\"http://schemas.xmlsoap.org/soap/envelope/\"><v:Header /><v:Body><" + method + " xmlns=\"http://tempuri.org/\" id=\"o0\" c:root=\"1\">" + "<sessionId i:type=\"d:string\">" + acc.getStringProperty(PROPERTY_ACCOUNT_API_SESSIONID, null) + "</sessionId>" + soapPost + "</" + method + "></v:Body></v:Envelope>";
        br.postPageRaw("http://api.save.tv/v2/Api.svc", postdata);
        /* Check for invalid sessionid --> Refresh if needed & perform request again */
        if (br.containsHTML(">invalid session id</ErrorMessage>")) {
            api_login(br, acc, true);
            postdata = "<?xml version=\"1.0\" encoding=\"utf-8\"?><v:Envelope xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:d=\"http://www.w3.org/2001/XMLSchema\" xmlns:c=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:v=\"http://schemas.xmlsoap.org/soap/envelope/\"><v:Header /><v:Body><" + method + " xmlns=\"http://tempuri.org/\" id=\"o0\" c:root=\"1\">" + "<sessionId i:type=\"d:string\">" + acc.getStringProperty(PROPERTY_ACCOUNT_API_SESSIONID, null) + "</sessionId>" + soapPost + "</" + method + "></v:Body></v:Envelope>";
            br.getHeaders().put("SOAPAction", soapAction);
            br.getHeaders().put("Content-Type", "text/xml");
            br.postPageRaw("http://api.save.tv/v2/Api.svc", postdata);
        }
    }

    private static void saveCookies(final Browser br, final Account acc) {
        /* Save cookies */
        final HashMap<String, String> cookies = new HashMap<String, String>();
        final Cookies add = br.getCookies(COOKIE_HOST);
        for (final Cookie c : add.getCookies()) {
            cookies.put(c.getKey(), c.getValue());
        }
        acc.setProperty("name", Encoding.urlEncode(acc.getUser()));
        acc.setProperty("pass", Encoding.urlEncode(acc.getPass()));
        acc.setProperty("cookies", cookies);
    }

    private boolean is_API_enabled() {
        return this.getPluginConfig().getBooleanProperty(USEAPI);
    }

    /* (Bad) workaround for issues with brackets in String that we regex */
    @SuppressWarnings("unused")
    private String getRegexSafe(final String input, final String regex, final int match) {
        final String regexFixedInput = input.replace("(", "65788jdclipopenjd4684").replace(")", "65788jdclipclosejd4684");
        String result = new Regex(regexFixedInput, regex).getMatch(match);
        if (result != null) {
            result = result.replace("65788jdclipopenjd4684", "(").replace("65788jdclipclosejd4684", ")");
        }
        return result;
    }

    /* Corrects all kinds of data which Stv provides, makes filenames look better */
    @SuppressWarnings("deprecation")
    public static String correctData(final String input) {
        String output = Encoding.htmlDecode(input);
        output = output.replace("_", " ");
        output = output.trim();
        output = output.replaceAll("(\r|\n)", "");
        output = output.replace("/", SubConfiguration.getConfig("save.tv").getStringProperty(CUSTOM_FILENAME_SEPERATION_MARK, defaultCustomSeperationMark));

        /* Correct spaces */
        final String[] unneededSpaces = new Regex(output, ".*?([ ]{2,}).*?").getColumn(0);
        if (unneededSpaces != null && unneededSpaces.length != 0) {
            for (String unneededSpace : unneededSpaces) {
                output = output.replace(unneededSpace, " ");
            }
        }
        return output;
    }

    @SuppressWarnings("deprecation")
    private static String getTelecastId(final DownloadLink link) {
        return new Regex(link.getDownloadURL(), "TelecastID=(\\d+)").getMatch(0);
    }

    /**
     * @return: Returns a random number for the DownloadLink - uses saved random number if existant - if not, creates random number and
     *          saves it.
     */
    private static String getRandomNumber(final DownloadLink dl) {
        String randomnumber = dl.getStringProperty("stv_randomnumber", null);
        if (randomnumber == null) {
            final DecimalFormat df = new DecimalFormat("0000");
            randomnumber = df.format(new Random().nextInt(10000));
            dl.setProperty("stv_randomnumber", randomnumber);
        }
        return randomnumber;
    }

    @SuppressWarnings("deprecation")
    public static String getFormattedFilename(final DownloadLink downloadLink) throws ParseException {
        final SubConfiguration cfg = SubConfiguration.getConfig("save.tv");
        final String customStringForEmptyTags = getCustomStringForEmptyTags();
        final String acc_username = cfg.getStringProperty("acc_username", customStringForEmptyTags);

        final String server_filename = downloadLink.getStringProperty("server_filename", customStringForEmptyTags);
        final String site_title = downloadLink.getStringProperty("plainfilename", customStringForEmptyTags);
        final String ext = downloadLink.getStringProperty("type", EXTENSION);
        final String quality = downloadLink.getStringProperty("quality", customStringForEmptyTags);
        final String genre = downloadLink.getStringProperty("genre", customStringForEmptyTags);
        final String producecountry = downloadLink.getStringProperty("producecountry", customStringForEmptyTags);
        final String produceyear = downloadLink.getStringProperty("produceyear", customStringForEmptyTags);
        final String randomnumber = getRandomNumber(downloadLink);
        final String telecastid = getTelecastId(downloadLink);
        final String tv_station = downloadLink.getStringProperty("plain_tv_station", customStringForEmptyTags);
        final String site_category = downloadLink.getStringProperty("plain_site_category", customStringForEmptyTags);
        /* For series */
        final String episodename = downloadLink.getStringProperty("episodename", customStringForEmptyTags);
        final String episodenumber = getEpisodeNumber(downloadLink);

        final long date = getLongProperty(downloadLink, "originaldate", 0l);
        String formattedDate = null;
        final String userDefinedDateFormat = cfg.getStringProperty(CUSTOM_DATE, defaultCustomDate);
        Date theDate = new Date(date);
        if (userDefinedDateFormat != null) {
            try {
                final SimpleDateFormat formatter = new SimpleDateFormat(userDefinedDateFormat);
                formattedDate = formatter.format(theDate);
            } catch (Exception e) {
                /* prevent user error killing plugin */
                formattedDate = defaultCustomStringForEmptyTags;
            }
        }

        String formattedFilename = null;
        if (!isSeries(downloadLink)) {
            /* For all links except series */
            formattedFilename = cfg.getStringProperty(CUSTOM_FILENAME_MOVIES, defaultCustomFilenameMovies);
            if (formattedFilename == null || formattedFilename.equals("")) {
                formattedFilename = defaultCustomFilenameMovies;
            }
            formattedFilename = formattedFilename.toLowerCase();
            /* Make sure that the user entered a VALID custom filename - if not, use the default name */
            if (!formattedFilename.contains("*endung*") || (!formattedFilename.contains("*videotitel*") && !formattedFilename.contains("*zufallszahl*") && !formattedFilename.contains("*telecastid*") && !formattedFilename.contains("*sendername*") && !formattedFilename.contains("*username*") && !formattedFilename.contains("*quality*") && !formattedFilename.contains("*server_dateiname*"))) {
                formattedFilename = defaultCustomFilenameMovies;
            }

            formattedFilename = formattedFilename.replace("*zufallszahl*", randomnumber);
            formattedFilename = formattedFilename.replace("*telecastid*", telecastid);
            formattedFilename = formattedFilename.replace("*datum*", formattedDate);
            formattedFilename = formattedFilename.replace("*produktionsjahr*", produceyear);
            formattedFilename = formattedFilename.replace("*endung*", ext);
            formattedFilename = formattedFilename.replace("*quality*", quality);
            formattedFilename = formattedFilename.replace("*sendername*", tv_station);
            formattedFilename = formattedFilename.replace("*kategorie*", site_category);
            formattedFilename = formattedFilename.replace("*genre*", genre);
            formattedFilename = formattedFilename.replace("*produktionsland*", producecountry);
            formattedFilename = formattedFilename.replace("*username*", acc_username);
            /* Insert actual filename at the end to prevent errors with tags */
            formattedFilename = formattedFilename.replace("*server_dateiname*", server_filename);
            formattedFilename = formattedFilename.replace("*videotitel*", site_title);
        } else {
            /* For series */
            formattedFilename = cfg.getStringProperty(CUSTOM_FILENAME_SERIES, defaultCustomSeriesFilename);
            if (formattedFilename == null || formattedFilename.equals("")) {
                formattedFilename = defaultCustomFilenameMovies;
            }
            formattedFilename = formattedFilename.toLowerCase();
            /* Make sure that the user entered a VALID custom filename - if not, use the default name */
            if (!formattedFilename.contains("*endung*") || (!formattedFilename.contains("*serientitel*") && !formattedFilename.contains("*episodenname*") && !formattedFilename.contains("*episodennummer*") && !formattedFilename.contains("*zufallszahl*") && !formattedFilename.contains("*telecastid*") && !formattedFilename.contains("*sendername*") && !formattedFilename.contains("*username*") && !formattedFilename.contains("*quality*") && !formattedFilename.contains("*server_dateiname*"))) {
                formattedFilename = defaultCustomFilenameMovies;
            }

            formattedFilename = formattedFilename.replace("*zufallszahl*", randomnumber);
            formattedFilename = formattedFilename.replace("*telecastid*", telecastid);
            formattedFilename = formattedFilename.replace("*datum*", formattedDate);
            formattedFilename = formattedFilename.replace("*produktionsjahr*", produceyear);
            formattedFilename = formattedFilename.replace("*episodennummer*", episodenumber);
            formattedFilename = formattedFilename.replace("*endung*", ext);
            formattedFilename = formattedFilename.replace("*quality*", quality);
            formattedFilename = formattedFilename.replace("*sendername*", tv_station);
            formattedFilename = formattedFilename.replace("*kategorie*", site_category);
            formattedFilename = formattedFilename.replace("*genre*", genre);
            formattedFilename = formattedFilename.replace("*produktionsland*", producecountry);
            formattedFilename = formattedFilename.replace("*username*", acc_username);
            /* Insert filename at the end to prevent errors with tags */
            formattedFilename = formattedFilename.replace("*server_dateiname*", server_filename);
            formattedFilename = formattedFilename.replace("*serientitel*", site_title);
            formattedFilename = formattedFilename.replace("*episodenname*", episodename);
        }
        formattedFilename = encodeUnicode(formattedFilename);

        formattedFilename = fixCharIssues(formattedFilename);
        return formattedFilename;
    }

    /** Returns either the original server filename or one that is very similar to the original */
    @SuppressWarnings("deprecation")
    public static String getFakeOriginalFilename(final DownloadLink downloadLink) throws ParseException {
        final SubConfiguration cfg = SubConfiguration.getConfig("save.tv");
        final String ext = downloadLink.getStringProperty("type", EXTENSION);

        final long date = getLongProperty(downloadLink, "originaldate", 0l);
        String formattedDate = null;
        /* Get correctly formatted date */
        String dateFormat = "yyyy-MM-dd";
        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy");
        Date theDate = new Date(date);
        try {
            formatter = new SimpleDateFormat(dateFormat);
            formattedDate = formatter.format(theDate);
        } catch (Exception e) {
            /* prevent user error killing plugin */
            formattedDate = "";
        }
        /* Get correctly formatted time */
        dateFormat = "HHmm";
        String time = "0000";
        try {
            formatter = new SimpleDateFormat(dateFormat);
            time = formatter.format(theDate);
        } catch (Exception e) {
            /* prevent user error killing plugin */
            time = "0000";
        }

        final String acc_username = cfg.getStringProperty("acc_username", getRandomNumber(downloadLink));
        String formattedFilename = downloadLink.getStringProperty("server_filename", null);
        if (formattedFilename != null) {
            /* Server = already original filename - no need to 'fake' anything */
            formattedFilename += EXTENSION;
        } else {
            final String title = convertNormalDataToServer(downloadLink.getStringProperty("plainfilename", getCustomStringForEmptyTags()));
            String episodename = downloadLink.getStringProperty("episodename", null);
            final String episodenumber = getEpisodeNumber(downloadLink);
            formattedFilename = title + "_";
            if (episodename != null) {
                episodename = convertNormalDataToServer(episodename);
                formattedFilename += episodename + "_";
            }
            /* Only add "Folge" if episodenumber is available */
            if (episodenumber.matches("\\d+")) {
                formattedFilename += "Folge" + episodenumber + "_";
            }

            formattedFilename += formattedDate + "_";
            formattedFilename += time + "_" + acc_username;
            /*
             * Finally, make sure we got no double underscores. Do this before we set the file extension es dots will be replaced within the
             * convertNormalDataToServer method!
             */
            formattedFilename = convertNormalDataToServer(formattedFilename);
            formattedFilename += ext;
            formattedFilename = encodeUnicode(formattedFilename);
        }
        formattedFilename = fixCharIssues(formattedFilename);
        return formattedFilename;
    }

    @SuppressWarnings("deprecation")
    private static String getEpisodeNumber(final DownloadLink dl) {
        /* Old way TODO: Remove after 11.2014 */
        String episodenumber = Long.toString(getLongProperty(dl, "episodenumber", 0l));
        if (episodenumber.equals("0")) {
            /* New way */
            episodenumber = dl.getStringProperty("episodenumber", null);
        }
        if (episodenumber == null) {
            return getCustomStringForEmptyTags();
        } else {
            return episodenumber;
        }
    }

    /**
     * @return true: DownloadLink is a series false: DownloadLink is no series based on existing information.
     */
    @SuppressWarnings("deprecation")
    private static boolean isSeries(final DownloadLink dl) {
        final String customStringForEmptyTags = getCustomStringForEmptyTags();
        /* For series */
        final String episodename = dl.getStringProperty("episodename", customStringForEmptyTags);
        final String episodenumber = getEpisodeNumber(dl);
        /* If we have an episodename and/or episodenumber, we have a series, category does not matter then */
        final boolean forceSeries = (!episodename.equals(customStringForEmptyTags) || episodenumber.matches("\\d+"));

        /* Check if we have a series or movie category */
        long cat = getLongProperty(dl, "category", 0l);
        final boolean belongsToCategoryMovie = (cat == 0 || cat == 1 || cat == 3 || cat == 7);

        final boolean isSeries = (forceSeries || !belongsToCategoryMovie);
        return isSeries;
    }

    /**
     * Returns the user-defined string to be used for empty filename-tags.Empty filename tag = needed data is not there (null) and
     * CUSTOM_FILENAME_EMPTY_TAG_STRING will be used instead.
     */
    @SuppressWarnings("deprecation")
    public static String getCustomStringForEmptyTags() {
        final SubConfiguration cfg = SubConfiguration.getConfig("save.tv");
        final String customStringForEmptyTags = cfg.getStringProperty(CUSTOM_FILENAME_EMPTY_TAG_STRING, defaultCustomStringForEmptyTags);
        return customStringForEmptyTags;
    }

    /**
     * Helps to get good looking original server-filenames, correct things, before corrected by correctData, in the end data/filename should
     * be 99% close to the originals. After all this does not have to be perfect as this data is only displayed to the user (e.g. a faked
     * 'original server filename' but NEVER used e.g. as a final filename.
     */
    private static String convertNormalDataToServer(String parameter) {
        /* Corrections with spaces */
        parameter = parameter.replace(" - ", "_");
        parameter = parameter.replace(" + ", "_");
        parameter = parameter.replace("(", "_");
        parameter = parameter.replace(")", "_");

        /* Correction via replaces */
        parameter = parameter.replace(" ", "_");
        parameter = parameter.replace("é", "_");
        parameter = parameter.replace("ä", "ae");
        parameter = parameter.replace("Ä", "Ae");
        parameter = parameter.replace("ö", "oe");
        parameter = parameter.replace("Ö", "Oe");
        parameter = parameter.replace("ü", "ue");
        parameter = parameter.replace("Ü", "Ue");
        parameter = parameter.replace("ß", "ss");
        /* Correction via replace remove */
        parameter = parameter.replace("+", "");
        parameter = parameter.replace("!", "");
        parameter = parameter.replace("'", "");
        parameter = parameter.replace("\"", "");
        parameter = parameter.replace("&", "");
        parameter = parameter.replace(",", "");
        parameter = parameter.replace(".", "");
        parameter = parameter.replace("?", "");
        /* Multiple underscores do never occur in server filenames --> Fix this here */
        final String[] underscores = new Regex(parameter, "(_{2,})").getColumn(0);
        if (underscores != null && underscores.length != 0) {
            for (final String underscoress : underscores) {
                parameter = parameter.replace(underscoress, "_");
            }
        }
        return parameter;
    }

    /* Stable workaround */
    public static long getLongProperty(final Property link, final String key, final long def) {
        try {
            return link.getLongProperty(key, def);
        } catch (final Throwable e) {
            try {
                Object r = link.getProperty(key, def);
                if (r instanceof String) {
                    r = Long.parseLong((String) r);
                } else if (r instanceof Integer) {
                    r = ((Integer) r).longValue();
                }
                final Long ret = (Long) r;
                return ret;
            } catch (final Throwable e2) {
                return def;
            }
        }
    }

    /* Helps to get good looking custom filenames out of server filenames */
    @SuppressWarnings("unused")
    private static String convertServerDataToNormal(String parameter) {
        parameter = parameter.replace("_", " ");

        parameter = parameter.replace("ae", "ä");
        parameter = parameter.replace("AE", "Ä");
        parameter = parameter.replace("Ae", "Ä");

        parameter = parameter.replace("oe", "ö");
        parameter = parameter.replace("OE", "Ö");
        parameter = parameter.replace("Oe", "Ö");

        parameter = parameter.replace("ue", "ü");
        parameter = parameter.replace("UE", "Ü");
        parameter = parameter.replace("Ue", "Ü");
        return parameter;
    }

    /* Correct characters of serverside encoding failures */
    private static String fixCharIssues(final String input) {
        String output = input;
        /* Part 1 */
        output = output.replace("ÃŸ", "ß");
        output = output.replace("Ã„", "Ä");
        output = output.replace("Ãœ", "Ü");
        output = output.replace("Ã–", "Ö");

        /* Part 2 */
        output = output.replace("Ã¶", "ö");
        output = output.replace("Ã¤", "ä");
        output = output.replace("Ã¼", "ü");
        // output = output.replace("Ã?", "");
        return output;
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
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public String getDescription() {
        return "JDownloader's Save.tv Plugin helps downloading videoclips from Save.tv. Save.tv provides different settings for its downloads.";
    }

    private final static String  defaultCustomFilenameMovies                = "*quality* ¦ *videotitel* ¦ *produktionsjahr* ¦ *telecastid**endung*";
    private final static String  defaultCustomSeriesFilename                = "*serientitel* ¦ *quality* ¦ *episodennummer* ¦ *episodenname* ¦ *telecastid**endung*";
    private final static String  defaultCustomSeperationMark                = "+";
    private final static String  defaultCustomStringForEmptyTags            = "-";
    private final static int     defaultCrawlLasthours                      = 0;
    private final static int     defaultADS_FREE_UNAVAILABLE_HOURS          = 12;
    private final static int     defaultADS_FREE_UNAVAILABLE_MAXRETRIES     = 0;
    private final static int     defaultFORCE_WITH_ADS_ON_ERROR_HOURS       = 12;
    private final static int     defaultFORCE_WITH_ADS_ON_ERROR_maxRetries  = 0;

    private static final boolean defaultDisableLinkcheck                    = false;
    private static final boolean defaultCrawlerActivate                     = false;
    private static final boolean defaultCrawlerFastLinkcheck                = true;
    private static final boolean defaultCrawlerAddNew                       = false;
    private static final boolean defaultInfoDialogsDisable                  = false;
    private static final boolean defaultPreferAdsFree                       = true;
    private static final boolean defaultUseOriginalFilename                 = false;
    private static final String  defaultCustomDate                          = "dd.MM.yyyy";
    private static final boolean defaultDeleteTelecastIDAfterDownload       = false;
    private static final boolean defaultDeleteTelecastIDIfFileAlreadyExists = false;

    private void setConfigElements() {
        /* Crawler settings */
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Archiv-Crawler Einstellungen:"));
        final ConfigEntry activateCrawler = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.CRAWLER_ACTIVATE, JDL.L("plugins.hoster.SaveTv.activateCrawler", "Archiv-Crawler aktivieren?\r\nINFO: Fügt das komplette Archiv oder Teile davon beim Einfügen dieses Links ein:\r\n'https://www.save.tv/STV/M/obj/archive/VideoArchive.cfm\r\n")).setDefaultValue(defaultCrawlerActivate);
        getConfig().addEntry(activateCrawler);
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.CRAWLER_ONLY_ADD_NEW_IDS, JDL.L("plugins.hoster.SaveTv.crawlerOnlyAddNewIDs", "Nur neue Aufnahmen hinzufügen?\r\nJDownloader gleicht dein save.tv Archiv ab mit den Einträgen, die du bereits eingefügt hast und zeigt immer nur neue Einträge an!\r\n<html><p style=\"color:#F62817\"><b>Information:</b>Dieses Feature ist noch nicht fertig, kommt aber bald!!</p></html>")).setDefaultValue(defaultCrawlerAddNew).setEnabled(false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, getPluginConfig(), SaveTv.CRAWLER_LASTHOURS_COUNT, JDL.L("plugins.hoster.SaveTv.grabArchive.lastHours", "Nur Aufnahmen der letzten X Stunden crawlen??\r\nAnzahl der Stunden, die gecrawlt werden sollen [0 = komplettes Archiv]:"), 0, 1000, 24).setDefaultValue(defaultCrawlLasthours).setEnabledCondidtion(activateCrawler, true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.CRAWLER_ENABLE_FASTER, JDL.L("plugins.hoster.SaveTv.grabArchiveFaster", "Aktiviere schnellen Linkcheck für Archiv-Crawler?\r\nVorteil: Über den Archiv-Crawler hinzugefügte Links landen viel schneller im Linkgrabber\r\nNachteil: Es sind nicht alle Informationen (z.B. Kategorie) verfügbar - erst beim Download oder späterem Linkcheck\r\n")).setDefaultValue(defaultCrawlerFastLinkcheck).setEnabledCondidtion(activateCrawler, true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.CRAWLER_DISABLE_DIALOGS, JDL.L("plugins.hoster.SaveTv.crawlerDisableDialogs", "Info Dialoge des Archiv-Crawlers (nach dem Crawlen oder im Fehlerfall) deaktivieren?")).setDefaultValue(defaultInfoDialogsDisable).setEnabledCondidtion(activateCrawler, true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));

        /* Format & Quality settings */
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Format & Qualitäts-Einstellungen:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX_INDEX, getPluginConfig(), selected_video_format, formats, JDL.L("plugins.hoster.SaveTv.prefer_format", "Bevorzugtes Format")).setDefaultValue(0));
        final ConfigEntry preferAdsFree = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.PREFERADSFREE, JDL.L("plugins.hoster.SaveTv.PreferAdFreeVideos", "Aufnahmen mit angewandter Schnittliste bevorzugen?")).setDefaultValue(defaultPreferAdsFree);
        getConfig().addEntry(preferAdsFree);
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, getPluginConfig(), SaveTv.ADS_FREE_UNAVAILABLE_HOURS, JDL.L("plugins.hoster.SaveTv.downloadOnlyAdsFreeRetryHours", "Zeit [in stunden] bis zum Neuversuch für Aufnahmen, die (noch) keine Schnittliste haben.\r\nINFO: Der Standardwert beträgt 12 Stunden, um die Server nicht unnötig zu belasten.\r\n"), 1, 24, 1).setDefaultValue(defaultADS_FREE_UNAVAILABLE_HOURS).setEnabledCondidtion(preferAdsFree, true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, getPluginConfig(), SaveTv.ADS_FREE_UNAVAILABLE_MAXRETRIES, JDL.L("plugins.hoster.SaveTv.ignoreOnlyAdsFreeAfterRetries_maxRetries", "Max Anzahl Neuversuche bis der Download der Version ohne Schnittliste erzwungen wird [0 = nie erzwingen]:"), 0, 100, 1).setDefaultValue(defaultADS_FREE_UNAVAILABLE_MAXRETRIES).setEnabledCondidtion(preferAdsFree, true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Einstelungen zur Fehlerbehandlung des Fehlers 'Leider enthält Ihre Aufnahme nur Werbung'."));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, getPluginConfig(), SaveTv.FORCE_WITH_ADS_ON_ERROR_HOURS, JDL.L("plugins.hoster.SaveTv.forceHQHours", "Zeit [in stunden] bis zum Neuversuch für Aufnahmen, die (noch) keine Schnittliste haben.\r\nINFO: Der Standardwert beträgt 12 Stunden, um die Server nicht unnötig zu belasten.\r\n"), 1, 24, 1).setDefaultValue(defaultFORCE_WITH_ADS_ON_ERROR_HOURS).setEnabledCondidtion(preferAdsFree, true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, getPluginConfig(), SaveTv.FORCE_WITH_ADS_ON_ERROR_MAXRETRIES, JDL.L("plugins.hoster.SaveTv.forceWithoutAdsMaxretries", "Max Anzahl Neuversuche bis der Download der Version ohne Schnittliste erzwungen wird [0 = nie erzwingen]:"), 0, 100, 1).setDefaultValue(defaultFORCE_WITH_ADS_ON_ERROR_maxRetries).setEnabledCondidtion(preferAdsFree, true));

        /* Filename settings */
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Dateiname Einstellungen:"));
        final ConfigEntry origName = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.USEORIGINALFILENAME, JDL.L("plugins.hoster.SaveTv.UseOriginalFilename", "Original (Server) Dateinamen verwenden? <html><b>[Erst beim Downloadstart sichtbar!]</b></html>")).setDefaultValue(defaultUseOriginalFilename);
        getConfig().addEntry(origName);
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_DATE, JDL.L("plugins.hoster.savetv.customdate", "Setze das Datumsformat:\r\nWichtige Information dazu:\r\nDas Datum erscheint im angegebenen Format im Dateinamen, allerdings nur,\r\nwenn man das *datum* Tag auch verwendet (siehe Benutzerdefinierte Dateinamen für Filme und Serien unten)")).setDefaultValue(defaultCustomDate).setEnabledCondidtion(origName, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        /* General settings description */
        final StringBuilder sbinfo = new StringBuilder();
        sbinfo.append("Erklärung zur Nutzung eigener Dateinamen:\r\n");
        sbinfo.append("Eigene Dateinamen lassen sich unten über ein Tag-System (siehe weiter unten) nutzen.\r\n");
        sbinfo.append("Das bedeutet, dass man die Struktur seiner gewünschten Dateinamen definieren kann.\r\n");
        sbinfo.append("Dabei hat man Tags wie z.B. *telecastid*, die dann durch Daten ersetzt werden.\r\n");
        sbinfo.append("Wichtig dabei ist, dass Tags immer mit einem Stern starten und enden.\r\n");
        sbinfo.append("Man darf nichts zwischen ein Tag schreiben z.B. *-telecastid-*, da das\r\n");
        sbinfo.append("zu unschönen Dateinamen führt und das Tag nicht die Daten ersetzt werden kann.\r\n");
        sbinfo.append("Wenn man die Tags trennen will muss man die anderen Zeichen zwischen Tags\r\n");
        sbinfo.append("z.B. '-*telecastid*-*endung*' -> Der Dateiname würde dann in etwa so aussehen: '-7573789-.mp4' (ohne die '')\r\n");
        sbinfo.append("WICHTIG: Tags, zu denen die Daten fehlen, werden standardmäßig durch '-' (Bindestrich) ersetzt!\r\n");
        sbinfo.append("Fehlen z.B. die Daten zu *genre*, steht statt statt dem Genre dann ein Bindestrich ('-') an dieser Stelle im Dateinamen.\r\n");
        sbinfo.append("Gut zu wissen: Statt dem Bindestrich lässt sich hierfür unten auch ein anderes Zeichen bzw. Zeichenfolge definieren.\r\n");
        sbinfo.append("Außerdem: Für Filme und Serien gibt es unterschiedliche Tags.\r\n");
        sbinfo.append("Kaputtmachen kannst du mit den Einstellungen prinzipiell nichts also probiere es einfach aus ;)\r\n");
        sbinfo.append("Tipp: Die Save.tv Plugin Einstellungen lassen sich rechts oben wieder auf ihre Standardwerte zurücksetzen!\r\n");
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, sbinfo.toString()).setEnabledCondidtion(origName, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        /* Filename settings for movies */
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME_MOVIES, JDL.L("plugins.hoster.savetv.customfilenamemovies", "Eigener Dateiname für Filme/Shows:")).setDefaultValue(defaultCustomFilenameMovies).setEnabledCondidtion(origName, false));
        final StringBuilder sb = new StringBuilder();
        sb.append("Erklärung der verfügbaren Tags:\r\n");
        sb.append("*server_dateiname* = Original Dateiname (ohne Dateiendung)\r\n");
        sb.append("*username* = Benutzername\r\n");
        sb.append("*datum* = Datum der Ausstrahlung der aufgenommenen Sendung\r\n[Erscheint im oben definierten Format, wird von der save.tv Seite ausgelesen]\r\n");
        sb.append("*genre* = Das Genre\r\n");
        sb.append("*produktionsland* = Name des Produktionslandes\r\n");
        sb.append("*produktionsjahr* = Produktionsjahr\r\n");
        sb.append("*sendername* = Name des TV-Senders auf dem die Sendung ausgestrahlt wurde");
        sb.append("*kategorie* = Kategorie, siehe telecast-ID Seite\r\n");
        sb.append("*quality* = Qualitätsstufe des Downloads - Entspricht den Werten 'LQ', 'HQ' oder 'HD'\r\n");
        sb.append("*videotitel* = Name des Videos ohne Dateiendung\r\n");
        sb.append("*zufallszahl* = Eine vierstellige Zufallszahl\r\n[Nützlich um Dateinamenkollisionen zu vermeiden]\r\n");
        sb.append("*telecastid* = Die id, die in jedem save.tv Link steht: TelecastID=XXXXXXX\r\n[Nützlich um Dateinamenkollisionen zu vermeiden]\r\n");
        sb.append("*endung* = Die Dateiendung, in diesem Fall immer '.mp4'");
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, sb.toString()).setEnabledCondidtion(origName, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        /* Filename settings for series */
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME_SERIES, JDL.L("plugins.hoster.savetv.customseriesfilename", "Eigener Dateiname für Serien:")).setDefaultValue(defaultCustomSeriesFilename).setEnabledCondidtion(origName, false));
        final StringBuilder sbseries = new StringBuilder();
        sbseries.append("Erklärung der verfügbaren Tags:\r\n");
        sbseries.append("*server_dateiname* = Original Dateiname (ohne Dateiendung)\r\n");
        sbseries.append("*username* = Benutzername\r\n");
        sbseries.append("*datum* = Datum der Ausstrahlung der aufgenommenen Sendung\r\n[Erscheint im oben definierten Format, wird von der save.tv Seite ausgelesen]\r\n");
        sbseries.append("*genre* = Das Genre\r\n");
        sbseries.append("*produktionsland* = Name des Produktionslandes\r\n");
        sbseries.append("*produktionsjahr* = Produktionsjahr\r\n");
        sbseries.append("*sendername* = Name des TV-Senders auf dem die Sendung ausgestrahlt wurde");
        sbseries.append("*kategorie* = Kategorie, siehe telecast-ID Seite\r\n");
        sbseries.append("*quality* = Qualitätsstufe des Downloads - Entspricht den Werten 'LQ', 'HQ' oder 'HD'\r\n");
        sbseries.append("*serientitel* = Name der Serie\r\n");
        sbseries.append("*episodenname* = Name der Episode\r\n");
        sbseries.append("*episodennummer* = Episodennummer\r\n");
        sbseries.append("*zufallszahl* = Eine vierstellige Zufallszahl\r\n[Nützlich um Dateinamenkollisionen zu vermeiden\r\n");
        sbseries.append("*telecastid* = Die id, die in jedem save.tv Link steht: TelecastID=XXXXXXX\r\n[Nützlich um Dateinamenkollisionen zu vermeiden]\r\n");
        sbseries.append("*endung* = Die Dateiendung, immer '.mp4'");
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, sbseries.toString()).setEnabledCondidtion(origName, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));

        /* Advanced settings */
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Erweiterte Einstellungen:\r\n<html><p style=\"color:#F62817\"><b>Warnung: Ändere diese Einstellungen nur, wenn du weißt was du tust!</b></p></html>"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.ACTIVATE_BETA_FEATURES, JDL.L("plugins.hoster.SaveTv.ActivateBETAFeatures", "Aktiviere BETA-Features?\r\nINFO: Was diese Features sind und ob es aktuell welche gibt steht im Support Forum.")));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.USEAPI, JDL.L("plugins.hoster.SaveTv.UseAPI", "API verwenden?\r\nINFO: Aktiviert man die API, sind einige Features wie folgt betroffen:\r\n-ENTFÄLLT: Option 'Nur Aufnahmen mit angewandter Schnittliste laden'\r\n-ENTFÄLLT: Anzeigen der Account Details in der Account-Verwaltung (Account Typ, Ablaufdatum, ...)\r\n-EINGESCHRÄNKT NUTZBAR: Benutzerdefinierte Dateinamen")));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.DISABLE_LINKCHECK, JDL.L("plugins.hoster.SaveTv.DisableLinkcheck", "Linkcheck deaktivieren <html><b>[Nicht empfohlen]</b>?\r\n<p style=\"color:#F62817\"><b>Vorteile:\r\n</b>-Links landen schneller im Linkgrabber und können auch bei Serverproblemen oder wenn die save.tv Seite komplett offline ist gesammelt werden\r\n<b>Nachteile:\r\n</b>-Im Linkgrabber werden zunächst nur die telecastIDs als Dateinamen angezeigt\r\n-Die endgültigen Dateinamen werden erst beim Downloadstart angezeigt</p></html>")).setDefaultValue(defaultDisableLinkcheck));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Soll die telecastID in irgendeinem Fall aus dem save.tv Archiv gelöscht werden?\r\n<html><p style=\"color:#F62817\"><b>Warnung:</b> Gelöschte telecastIDs können nicht wiederhergestellt werden!</p></html>"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.DELETE_TELECAST_ID_AFTER_DOWNLOAD, JDL.L("plugins.hoster.SaveTv.deleteFromArchiveAfterDownload", "Erfolgreich geladene telecastIDs aus dem save.tv Archiv löschen?")).setDefaultValue(defaultDeleteTelecastIDAfterDownload));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SaveTv.DELETE_TELECAST_ID_IF_FILE_ALREADY_EXISTS, JDL.L("plugins.hoster.SaveTv.deleteFromArchiveIfFileAlreadyExists", "Falls Datei bereits auf der Festplatte existiert telecastIDs aus dem save.tv Archiv löschen?")).setDefaultValue(defaultDeleteTelecastIDIfFileAlreadyExists));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME_SEPERATION_MARK, JDL.L("plugins.hoster.savetv.customFilenameSeperationmark", "Trennzeichen als Ersatz für '/'  (da ungültig in Dateinamen):")).setDefaultValue(defaultCustomSeperationMark).setEnabledCondidtion(origName, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME_EMPTY_TAG_STRING, JDL.L("plugins.hoster.savetv.customEmptyTagsString", "Zeichen, mit dem Tags ersetzt werden sollen, deren Daten fehlen:")).setDefaultValue(defaultCustomStringForEmptyTags).setEnabledCondidtion(origName, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        final StringBuilder sbmore = new StringBuilder();
        sbmore.append("Definiere Filme oder Serien, für die trotz obiger Einstellungen die Originaldateinamen\r\n");
        sbmore.append("verwendet werden sollen.\r\n");
        sbmore.append("Manche mehrteiligen Filme haben dieselben Titel und bei manchen Serien fehlen die Episodennamen,\r\n");
        sbmore.append("wodurch sie alle dieselben Dateinamen bekommen -> JDownloader denkt es seien Duplikate/Mirrors und lädt nur\r\n");
        sbmore.append("einen der scheinbar gleichen Dateien.\r\n");
        sbmore.append("Um dies zu verhindern, kann man in den Eingabefeldern Namen solcher Filme/Serien eintragen,\r\n");
        sbmore.append("für die trotz obiger Einstellungen der Original Dateiname verwendet werden soll.\r\n");
        sbmore.append("Beispiel: 'serienname 1|serienname 2|usw.' (ohne die '')\r\n");
        sbmore.append("Die Eingabe erfolgt als RegEx. Wer nicht weiß was das ist -> Google");
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, sbmore.toString()).setEnabledCondidtion(origName, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), FORCE_ORIGINALFILENAME_SERIES, JDL.L("plugins.hoster.savetv.forceoriginalnameforspecifiedseries", "Original Dateinamen für folgende Serien erzwingen [Eingabe erfolgt in RegEx]:")).setDefaultValue("").setEnabledCondidtion(origName, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), FORCE_ORIGINALFILENAME_MOVIES, JDL.L("plugins.hoster.savetv.forcefilenameforspecifiedmovies", "Original Dateinamen für folgende Filme erzwingen [Eingabe erfolgt in RegEx]:")).setDefaultValue("").setEnabledCondidtion(origName, false));
    }

    private static String getMessageEnd() {
        String message = "";
        message += "\r\n\r\n";
        message += "Falls du Fehler findest oder Fragen hast, melde dich jederzeit gerne bei uns im Supportforum:\r\nhttp://board.jdownloader.org/\r\n";
        message += "\r\n";
        message += "Dieses Fenster wird nur einmal angezeigt.\r\nAlle wichtigen Informationen stehen auch in den save.tv Plugin Einstellungen.\r\n";
        message += "\r\n";
        message += "- Das JDownloader Team wünscht weiterhin viel Spaß mit JDownloader und save.tv! -";
        return message;
    }

    @SuppressWarnings("deprecation")
    private void checkAccountNeededDialog() {
        SubConfiguration config = null;
        try {
            config = getPluginConfig();
            if (config.getBooleanProperty("accNeededShown", Boolean.FALSE) == false) {
                if (config.getProperty("accNeededShown2") == null) {
                    showAccNeededDialog();
                } else {
                    config = null;
                }
            } else {
                config = null;
            }
        } catch (final Throwable e) {
        } finally {
            if (config != null) {
                config.setProperty("accNeededShown", Boolean.TRUE);
                config.setProperty("accNeededShown2", "shown");
                config.save();
            }
        }
    }

    private static void showAccNeededDialog() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    try {
                        String message = "";
                        String title = null;
                        title = "Save.tv - Account benötigt";
                        message += "Hallo lieber save.tv Nutzer.\r\n";
                        message += "Um über JDownloader Videos aus deinem save.tv Archiv herunterladen zu können musst du\r\nzunächst deinen save.tv Account in JDownloader eintragen.";
                        message += "\r\n";
                        message += "In JDownloader 0.9.581 geht das unter:\r\n";
                        message += "Einstellungen -> Anbieter -> -> Premium -> Account hinzufügen save.tv\r\n";
                        message += "\r\n";
                        message += "In der JDownloader 2 BETA geht das unter:\r\n";
                        message += "Einstellungen -> Accountverwaltung -> Hinzufügen -> save.tv\r\n";
                        message += "\r\n";
                        message += "Sobald du deinen Account eingetragen hast kannst du aus deinem save.tv Archiv\r\n";
                        message += "Links dieses Formats in JDownloader einfügen und herunterladen:\r\n";
                        message += "save.tv/STV/M/obj/user/usShowVideoArchiveDetail.cfm?TelecastID=XXXXXXX";
                        message += getMessageEnd();
                        JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.PLAIN_MESSAGE, JOptionPane.INFORMATION_MESSAGE, null);
                    } catch (Throwable e) {
                    }
                }
            });
        } catch (Throwable e) {
        }
    }

    @SuppressWarnings("deprecation")
    private void checkFeatureDialogAll() {
        SubConfiguration config = null;
        try {
            config = getPluginConfig();
            if (config.getBooleanProperty("featuredialog_all_Shown", Boolean.FALSE) == false) {
                if (config.getProperty("featuredialog_all_Shown2") == null) {
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
                config.setProperty("featuredialog_all_Shown", Boolean.TRUE);
                config.setProperty("featuredialog_all_Shown2", "shown");
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
                        title = "Save.tv Plugin - Features";
                        message += "Hallo lieber save.tv Nutzer/liebe save.tv Nutzerin\r\n";
                        message += "Das save.tv Plugin bietet folgende Features:\r\n";
                        message += "- Automatisierter Download von save.tv Links (telecast-IDs)\r\n";
                        message += "- Laden des kompletten save.tv Archivs über wenige Klicks\r\n";
                        message += "-- > Oder wahlweise nur alle Links der letzten X Tage/Stunden\r\n";
                        message += "- Benutzerdefinierte Dateinamen über ein Tag-System mit vielen Möglichkeiten\r\n";
                        message += "- Alles unter beachtung der Schnittlisten-Einstellungen und des Formats\r\n";
                        message += "- Und viele mehr...\r\n";
                        message += "\r\n";
                        message += "Diese Einstellungen sind nur in der Version JDownloader 2 BETA verfügbar unter:\r\nEinstellungen -> Plugin Einstellungen -> save.tv";
                        message += getMessageEnd();
                        JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.PLAIN_MESSAGE, JOptionPane.INFORMATION_MESSAGE, null);
                    } catch (Throwable e) {
                    }
                }
            });
        } catch (Throwable e) {
        }
    }

    @SuppressWarnings("deprecation")
    private void checkFeatureDialogCrawler() {
        SubConfiguration config = null;
        try {
            config = getPluginConfig();
            if (config.getBooleanProperty("featuredialog_crawler_Shown", Boolean.FALSE) == false) {
                if (config.getProperty("featuredialog_crawler_Shown2") == null) {
                    showFeatureDialogCrawler();
                } else {
                    config = null;
                }
            } else {
                config = null;
            }
        } catch (final Throwable e) {
        } finally {
            if (config != null) {
                config.setProperty("featuredialog_crawler_Shown", Boolean.TRUE);
                config.setProperty("featuredialog_crawler_Shown2", "shown");
                config.save();
            }
        }
    }

    private static void showFeatureDialogCrawler() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    try {
                        String message = "";
                        String title = null;
                        title = "Save.tv Plugin - Crawler Features";
                        message += "Hallo lieber save.tv Nutzer/liebe save.tv NutzerIn\r\n";
                        message += "Das save.tv Crawler Plugin bietet folgende Features:\r\n";
                        message += "- Info-Dialoge\r\n";
                        message += "- Viele Dateiinfos trotz aktiviertem schnellen Linkcheck\r\n";
                        message += "- Eigene Dateinamen auch bei aktiviertem schnellen Linkcheck (eingeschränkt)\r\n";
                        message += "- Die Möglichkeit, wahlweise alle oder nur Aufnahmen der letzten X Stunden zu crawlen\r\n";
                        message += "\r\n";
                        message += "Um den Crawler nutzen zu können, musst du ihn erst in den Plugin Einstellungen aktivieren.\r\n";
                        message += "\r\n";
                        message += "Die Crawler Einstellungen sind nur in der Version JDownloader 2 BETA verfügbar unter:\r\nEinstellungen -> Plugin Einstellungen -> save.tv";
                        message += getMessageEnd();
                        JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.PLAIN_MESSAGE, JOptionPane.INFORMATION_MESSAGE, null);
                    } catch (Throwable e) {
                    }
                }
            });
        } catch (Throwable e) {
        }
    }

    public void showAccountDetailsDialog(final Account account) {
        final AccountInfo ai = account.getAccountInfo();
        if (ai != null) {
            final String windowTitleLangText = "Account Zusatzinformationen";
            final String accType = account.getStringProperty("acc_type", "Premium Account");
            final String accUsername = account.getStringProperty("acc_username", "?");
            final String acc_expire = account.getStringProperty("acc_expire", "?");
            final String acc_package = account.getStringProperty("acc_package", "?");
            final String acc_price = account.getStringProperty("acc_price", "?");
            final String acc_runtime = account.getStringProperty("acc_runtime", "?");
            final String acc_count_archive_entries = account.getStringProperty("acc_count_archive_entries", "?");
            final String acc_count_telecast_ids = account.getStringProperty("acc_count_telecast_ids", "?");

            final String user_lastcrawl_date;
            final long time_last_crawl_ended = getLongProperty(this.getPluginConfig(), CRAWLER_PROPERTY_LASTCRAWL, 0);

            if (time_last_crawl_ended > 0) {
                final SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy 'um' HH:mm 'Uhr'");
                user_lastcrawl_date = formatter.format(time_last_crawl_ended);
            } else {
                user_lastcrawl_date = "Nie";
            }

            /* it manages new panel */
            final PanelGenerator panelGenerator = new PanelGenerator();

            JLabel hostLabel = new JLabel("<html><b>" + account.getHoster() + "</b></html>");
            hostLabel.setIcon(DomainInfo.getInstance(account.getHoster()).getFavIcon());
            panelGenerator.addLabel(hostLabel);

            String revision = "$Revision$";
            try {
                String[] revisions = revision.split(":");
                revision = revisions[1].replace('$', ' ').trim();
            } catch (final Exception e) {
                logger.info(this.getHost() + " revision number error: " + e);
            }

            panelGenerator.addCategory("Account");
            panelGenerator.addEntry("Name:", account.getUser());
            panelGenerator.addEntry("Username:", accUsername);
            panelGenerator.addEntry("Account Typ:", accType);
            panelGenerator.addEntry("Paket:", acc_package);
            panelGenerator.addEntry("Laufzeit:", acc_runtime);
            panelGenerator.addEntry("Ablaufdatum:", acc_expire + " Uhr");
            panelGenerator.addEntry("Preis:", acc_price);
            panelGenerator.addEntry("Sendungen im Archiv:", acc_count_archive_entries);
            panelGenerator.addEntry("Ladbare Sendungen im Archiv (telecast-IDs):", acc_count_telecast_ids);
            panelGenerator.addEntry("Datum des letzten erfolgreichen Crawlvorganges: ", user_lastcrawl_date);

            panelGenerator.addCategory("Download");
            panelGenerator.addEntry("Max. Anzahl gleichzeitiger Downloads:", "20");
            panelGenerator.addEntry("Max. Anzahl Verbindungen pro Datei (Chunks):", "2");
            panelGenerator.addEntry("Abgebrochene Downloads fortsetzbar:", "Ja");

            panelGenerator.addEntry("Plugin Revision:", revision);

            ContainerDialog dialog = new ContainerDialog(UIOManager.BUTTONS_HIDE_CANCEL + UIOManager.LOGIC_COUNTDOWN, windowTitleLangText, panelGenerator.getPanel(), null, "Schließen", "");
            try {
                Dialog.getInstance().showDialog(dialog);
            } catch (DialogNoAnswerException e) {
            }
        }

    }

    public class PanelGenerator {
        private JPanel panel = new JPanel();
        private int    y     = 0;

        public PanelGenerator() {
            panel.setLayout(new GridBagLayout());
            panel.setMinimumSize(new Dimension(270, 200));
        }

        public void addLabel(JLabel label) {
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.gridx = 0;
            c.gridwidth = 2;
            c.gridy = y;
            c.insets = new Insets(0, 5, 0, 5);
            panel.add(label, c);
            y++;
        }

        public void addCategory(String categoryName) {
            JLabel category = new JLabel("<html><u><b>" + categoryName + "</b></u></html>");

            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.gridx = 0;
            c.gridwidth = 2;
            c.gridy = y;
            c.insets = new Insets(10, 5, 0, 5);
            panel.add(category, c);
            y++;
        }

        public void addEntry(String key, String value) {
            GridBagConstraints c = new GridBagConstraints();
            JLabel keyLabel = new JLabel(key);
            // keyLabel.setFont(keyLabel.getFont().deriveFont(Font.BOLD));
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 0.9;
            c.gridx = 0;
            c.gridy = y;
            c.insets = new Insets(0, 5, 0, 5);
            panel.add(keyLabel, c);

            JLabel valueLabel = new JLabel(value);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.gridx = 1;
            panel.add(valueLabel, c);

            y++;
        }

        public void addTextField(JTextArea textfield) {
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.gridx = 0;
            c.gridwidth = 2;
            c.gridy = y;
            c.insets = new Insets(0, 5, 0, 5);
            panel.add(textfield, c);
            y++;
        }

        public JPanel getPanel() {
            return panel;
        }

    }

}