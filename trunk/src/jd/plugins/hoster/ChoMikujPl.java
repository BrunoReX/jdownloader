//    jDownloader - Downloadmanager
//    Copyright (C) 2013  JD-Team support@jdownloader.org
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "chomikuj.pl" }, urls = { "http://chomikujdecrypted\\.pl/.*?,\\d+$" })
public class ChoMikujPl extends PluginForHost {

    private String              DLLINK                      = null;

    private static final String PREMIUMONLY                 = "(Aby pobrać ten plik, musisz być zalogowany lub wysłać jeden SMS\\.|Właściciel tego chomika udostępnia swój transfer, ale nie ma go już w wystarczającej|wymaga opłacenia kosztów transferu z serwerów Chomikuj\\.pl)";
    private static final String PREMIUMONLYUSERTEXT         = "Download is only available for registered/premium users!";
    private static final String ACCESSDENIED                = "Nie masz w tej chwili uprawnień do tego pliku lub dostęp do niego nie jest w tej chwili możliwy z innych powodów\\.";
    private final String        VIDEOENDINGS                = "\\.(avi|flv|mp4|mpg|rmvb|divx|wmv|mkv)";
    private static final String MAINPAGE                    = "http://chomikuj.pl/";
    private static Object       LOCK                        = new Object();
    /* Pluging settings */
    public static final String  DECRYPTFOLDERS              = "DECRYPTFOLDERS";
    private static final String AVOIDPREMIUMMP3TRAFFICUSAGE = "AVOIDPREMIUMMP3TRAFFICUSAGE";

    private static boolean      pluginloaded                = false;
    private Browser             cbr                         = null;

    private int                 free_maxchunks              = 1;
    private boolean             free_resume                 = false;
    private int                 free_maxdls                 = -1;

    private int                 account_maxchunks           = 0;
    /* TODO: Verify if premium users really can resume */
    private boolean             account_resume              = true;
    private int                 account_maxdls              = -1;
    private boolean             serverIssue                 = false;
    private boolean             premiumonly                 = false;
    private boolean             plus18                      = false;

    /* ChomikujPlScript */
    public ChoMikujPl(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://chomikuj.pl/Create.aspx");
        setConfigElements();
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("chomikujdecrypted.pl/", "chomikuj.pl/"));
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        serverIssue = false;
        premiumonly = false;
        plus18 = false;
        this.setBrowserExclusive();
        prepBR(this.br);
        final String mainlink = link.getStringProperty("mainlink", null);
        // Offline from decrypter
        if (link.getBooleanProperty("offline", false)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (mainlink != null) {
            /* Try to find better filename - usually only needed for single links. */
            br.getPage(mainlink);
            if (this.br.getHttpConnection().getResponseCode() == 404) {
                /* Additional offline check */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            String filename = br.getRegex("property=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
            if (filename != null) {
                logger.info("Found html filename for single link");
                filename = Encoding.htmlDecode(filename).trim();
                link.setFinalFileName(filename);
            } else {
                logger.info("Failed to find html filename for single link");
            }
        }
        plus18 = this.br.containsHTML("\"FormAdultViewAccepted\"");
        if (!plus18) {
            if (!getDllink(link, br.cloneBrowser(), false)) {
                premiumonly = true;
                link.getLinkStatus().setStatusText(JDL.L("plugins.hoster.chomikujpl.only4registered", PREMIUMONLYUSERTEXT));
                return AvailableStatus.TRUE;
            }
            if (cbr.containsHTML("Najprawdopodobniej plik został w miedzyczasie usunięty z konta")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (cbr.containsHTML(PREMIUMONLY)) {
                link.getLinkStatus().setStatusText(JDL.L("plugins.hoster.chomikujpl.only4registered", PREMIUMONLYUSERTEXT));
                return AvailableStatus.TRUE;
            }
        }
        if (DLLINK != null) {
            // In case the link redirects to the finallink
            br.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            try {
                con = br.openHeadConnection(DLLINK);
                if (!con.getContentType().contains("html")) {
                    link.setDownloadSize(con.getLongContentLength());
                    // Only set final filename if it wasn't set before as video and
                    // audio streams can have bad filenames
                    if (link.getFinalFileName() == null) {
                        link.setFinalFileName(Encoding.htmlDecode(getFileNameFromHeader(con)));
                    }
                } else {
                    /* Just because we get html here that doesn't mean that the file is offline ... */
                    serverIssue = true;
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    private static synchronized String unescape(final String s) {
        /* we have to make sure the youtube plugin is loaded */
        if (pluginloaded == false) {
            final PluginForHost plugin = JDUtilities.getPluginForHost("youtube.com");
            if (plugin == null) {
                throw new IllegalStateException("youtube plugin not found!");
            }
            pluginloaded = true;
        }
        return jd.nutils.encoding.Encoding.unescapeYoutube(s);
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        try {
            login(account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        account.setValid(true);
        final String remainingTraffic = br.getRegex("<strong>([^<>\"]*?)</strong>[\t\n\r ]+transferu").getMatch(0);
        if (remainingTraffic != null) {
            /* Basically uploaders can always download their own files no matter how much traffic they have left ... */
            ai.setSpecialTraffic(true);
            ai.setTrafficLeft(SizeFormatter.getSize(remainingTraffic.replace(",", ".")));
        } else {
            ai.setUnlimitedTraffic();
        }
        ai.setStatus("Premium account");
        try {
        } catch (final Throwable e) {
            account.setType(AccountType.PREMIUM);
            /* Not available in old 0.9.581 Stable */
        }
        return ai;
    }

    @Override
    public String getAGBLink() {
        return "http://chomikuj.pl/Regulamin.aspx";
    }

    @SuppressWarnings("deprecation")
    public boolean getDllink(final DownloadLink theLink, final Browser br, final boolean premium) throws Exception {
        final boolean redirectsSetting = br.isFollowingRedirects();
        br.setFollowRedirects(false);
        final String fid = getFID(theLink);
        // Set by the decrypter if the link is password protected
        String savedLink = theLink.getStringProperty("savedlink");
        String savedPost = theLink.getStringProperty("savedpost");
        if (savedLink != null && savedPost != null) {
            br.postPage(savedLink, savedPost);
        }
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        /* Premium users can always download the original file */
        if (isVideo(theLink) && !premium) {
            br.setFollowRedirects(true);
            getPage(br, "http://chomikuj.pl/ShowVideo.aspx?id=" + fid);
            if (br.getURL().contains("chomikuj.pl/Error404.aspx") || cbr.containsHTML("(Nie znaleziono|Strona, której szukasz nie została odnaleziona w portalu\\.<|>Sprawdź czy na pewno posługujesz się dobrym adresem)")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            br.setFollowRedirects(false);
            br.getPage("http://chomikuj.pl/Video.ashx?id=" + fid + "&type=1&ts=" + new Random().nextInt(1000000000) + "&file=video&start=0");
            DLLINK = br.getRedirectLocation();
            if (DLLINK == null) {
                /* Probably not free downloadable! */
                return false;
            }
            theLink.setFinalFileName(theLink.getName());
        } else if (theLink.getName().toLowerCase().endsWith(".mp3") && !premium) {
            DLLINK = getDllinkMP3(theLink);
            theLink.setFinalFileName(theLink.getName());
        } else {
            getPage(br, "http://chomikuj.pl/action/fileDetails/Index/" + fid);
            final String filesize = br.getRegex("<p class=\"fileSize\">([^<>\"]*?)</p>").getMatch(0);
            if (filesize != null) {
                theLink.setDownloadSize(SizeFormatter.getSize(filesize.replace(",", ".")));
            }
            if (br.getRedirectLocation() != null && br.getRedirectLocation().contains("fileDetails/Unavailable")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            String requestVerificationToken = theLink.getStringProperty("requestverificationtoken");
            if (requestVerificationToken == null) {
                br.setFollowRedirects(true);
                br.getPage(theLink.getDownloadURL());
                br.setFollowRedirects(false);
                requestVerificationToken = br.getRegex("<div id=\"content\">[\t\n\r ]+<input name=\"__RequestVerificationToken\" type=\"hidden\" value=\"([^<>\"]*?)\"").getMatch(0);
            }
            if (requestVerificationToken == null) {
                requestVerificationToken = theLink.getStringProperty("__RequestVerificationToken_Lw__", null);
            }
            if (requestVerificationToken == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.setCookie("http://chomikuj.pl/", "__RequestVerificationToken_Lw__", requestVerificationToken);

            final String chomikID = theLink.getStringProperty("chomikID");

            if (chomikID != null) {
                final String folderPassword = theLink.getStringProperty("password");

                if (folderPassword != null) {
                    br.setCookie("http://chomikuj.pl/", "FoldersAccess", String.format("%s=%s", chomikID, folderPassword));
                } else {
                    logger.warning("Failed to set FoldersAccess cookie inside getDllink");
                    // this link won't work without password
                    return false;
                }
            }

            postPage(br, "http://chomikuj.pl/action/License/Download", "fileId=" + fid + "&__RequestVerificationToken=" + Encoding.urlEncode(requestVerificationToken));
            if (cbr.containsHTML(PREMIUMONLY)) {
                return false;
            }
            if (cbr.containsHTML(ACCESSDENIED)) {
                return false;
            }
            DLLINK = br.getRegex("redirectUrl\":\"(http://.*?)\"").getMatch(0);
            if (DLLINK == null) {
                DLLINK = br.getRegex("\\\\u003ca href=\\\\\"([^\"]*?)\\\\\" title").getMatch(0);
            }
            if (DLLINK == null) {
                DLLINK = br.getRegex("\"(http://[A-Za-z0-9\\-_\\.]+\\.chomikuj\\.pl/File\\.aspx[^<>\"]*?)\\\\\"").getMatch(0);
            }
            if (DLLINK != null) {
                DLLINK = Encoding.htmlDecode(DLLINK);
            }
        }
        if (DLLINK != null) {
            DLLINK = unescape(DLLINK);
        }
        br.setFollowRedirects(redirectsSetting);
        return true;
    }

    private boolean isVideo(final DownloadLink dl) {
        String filename = dl.getFinalFileName();
        if (filename == null) {
            filename = dl.getName();
        }
        if (filename.contains(".")) {
            final String ext = filename.substring(filename.lastIndexOf("."));
            if (ext.matches(VIDEOENDINGS)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    public boolean getDllink_premium(final DownloadLink theLink, final Browser br, final boolean premium) throws Exception {
        final boolean redirectsSetting = br.isFollowingRedirects();
        br.setFollowRedirects(false);
        final String fid = this.getFID(theLink);
        /* Set by the decrypter if the link is password protected */
        String savedLink = theLink.getStringProperty("savedlink");
        String savedPost = theLink.getStringProperty("savedpost");
        if (savedLink != null && savedPost != null) {
            br.postPage(savedLink, savedPost);
        }
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        if (this.getPluginConfig().getBooleanProperty(AVOIDPREMIUMMP3TRAFFICUSAGE, false) && theLink.getName().toLowerCase().endsWith(".mp3")) {
            /* User wants to force stream download for .mp3 files --> Does not use up any premium traffic. */
            DLLINK = getDllinkMP3(theLink);
        } else {
            /* Premium users can always download the original file */
            getPage(br, "http://chomikuj.pl/action/fileDetails/Index/" + fid);
            final String filesize = br.getRegex("<p class=\"fileSize\">([^<>\"]*?)</p>").getMatch(0);
            if (filesize != null) {
                theLink.setDownloadSize(SizeFormatter.getSize(filesize.replace(",", ".")));
            }
            if (br.getRedirectLocation() != null && br.getRedirectLocation().contains("fileDetails/Unavailable")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            String requestVerificationToken = theLink.getStringProperty("requestverificationtoken");
            if (requestVerificationToken == null) {
                br.setFollowRedirects(true);
                getPage(br, theLink.getDownloadURL());
                br.setFollowRedirects(false);
                requestVerificationToken = cbr.getRegex("<div id=\"content\">[\t\n\r ]+<input name=\"__RequestVerificationToken\" type=\"hidden\" value=\"([^<>\"]*?)\"").getMatch(0);
            }
            if (requestVerificationToken == null) {
                requestVerificationToken = theLink.getStringProperty("__RequestVerificationToken_Lw__", null);
            }
            if (requestVerificationToken == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.setCookie("http://chomikuj.pl/", "__RequestVerificationToken_Lw__", requestVerificationToken);

            final String chomikID = theLink.getStringProperty("chomikID");

            if (chomikID != null) {
                final String folderPassword = theLink.getStringProperty("password");

                if (folderPassword != null) {
                    br.setCookie("http://chomikuj.pl/", "FoldersAccess", String.format("%s=%s", chomikID, folderPassword));
                } else {
                    logger.warning("Failed to set FoldersAccess cookie inside getDllink");
                    // this link won't work without password
                    return false;
                }
            }

            br.getHeaders().put("Referer", theLink.getDownloadURL());
            postPage(br, "http://chomikuj.pl/action/License/DownloadContext", "fileId=" + fid + "&__RequestVerificationToken=" + Encoding.urlEncode(requestVerificationToken));
            if (cbr.containsHTML(ACCESSDENIED)) {
                return false;
            }
            /* Low traffic warning */
            if (cbr.containsHTML("action=\"/action/License/DownloadWarningAccept\"")) {
                final String serializedUserSelection = cbr.getRegex("name=\"SerializedUserSelection\" type=\"hidden\" value=\"([^<>\"]*?)\"").getMatch(0);
                final String serializedOrgFile = cbr.getRegex("name=\"SerializedOrgFile\" type=\"hidden\" value=\"([^<>\"]*?)\"").getMatch(0);
                if (serializedUserSelection == null || serializedOrgFile == null) {
                    logger.warning("Failed to pass low traffic warning!");
                    return false;
                }
                postPage(br, "http://chomikuj.pl/action/License/DownloadWarningAccept", "FileId=" + fid + "&SerializedUserSelection=" + Encoding.urlEncode(serializedUserSelection) + "&SerializedOrgFile=" + Encoding.urlEncode(serializedOrgFile) + "&__RequestVerificationToken=" + Encoding.urlEncode(requestVerificationToken));
            }
            if (cbr.containsHTML("/action/License/acceptLargeTransfer")) {
                // this can happen also
                // problem is.. general cleanup is wrong, response is = Content-Type: application/json; charset=utf-8
                cleanupBrowser(cbr, PluginJSonUtils.unescape(br.toString()));
                // so we can get output in logger for debug purposes.
                logger.info(cbr.toString());
                final Form f = cbr.getFormbyAction("/action/License/acceptLargeTransfer");
                if (f == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                submitForm(br, f);
            } else if (cbr.containsHTML("/action/License/AcceptOwnTransfer")) {
                /*
                 * Some files on chomikuj hoster are available to download using transfer from file owner. When there's no owner transfer
                 * left then transfer is reduced from downloader account (downloader is asked if he wants to use his own transfer). We have
                 * to confirm this here.
                 */
                // problem is.. general cleanup is wrong, response is = Content-Type: application/json; charset=utf-8
                cleanupBrowser(cbr, PluginJSonUtils.unescape(br.toString()));
                // so we can get output in logger for debug purposes.
                logger.info(cbr.toString());
                final Form f = cbr.getFormbyAction("/action/License/AcceptOwnTransfer");
                if (f == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                f.remove(null);
                f.remove(null);
                f.put("__RequestVerificationToken", Encoding.urlEncode(requestVerificationToken));
                submitForm(br, f);
            }

            DLLINK = br.getRegex("redirectUrl\":\"(http://.*?)\"").getMatch(0);
            if (DLLINK == null) {
                DLLINK = br.getRegex("\\\\u003ca href=\\\\\"([^\"]*?)\\\\\" title").getMatch(0);
            }
            if (DLLINK == null) {
                DLLINK = br.getRegex("\"(http://[A-Za-z0-9\\-_\\.]+\\.chomikuj\\.pl/File\\.aspx[^<>\"]*?)\\\\\"").getMatch(0);
            }
            if (DLLINK != null) {
                DLLINK = unescape(DLLINK);
                DLLINK = Encoding.htmlDecode(DLLINK);
                if (DLLINK.contains("#SliderTransfer")) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "Traffic limit reached", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                }
            }
            br.setFollowRedirects(redirectsSetting);
        }
        return true;
    }

    private String getDllinkMP3(final DownloadLink dl) throws Exception {
        final String fid = getFID(dl);
        getPage(br, "http://chomikuj.pl/Audio.ashx?id=" + fid + "&type=2&tp=mp3");
        String dllink = br.getRedirectLocation();
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = unescape(dllink);
        free_resume = false;
        account_resume = false;
        return dllink;
    }

    private String getFID(final DownloadLink dl) {
        return dl.getStringProperty("fileid");
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_maxdls;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return account_maxdls;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        if (plus18) {
            logger.info("Adult content only downloadable when logged in");
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        } else if (serverIssue) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 30 * 60 * 1000l);
        } else if (cbr.containsHTML(PREMIUMONLY) || premiumonly) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
        }
        if (!isVideo(downloadLink)) {
            if (!getDllink(downloadLink, br, false)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            }
        }
        if (DLLINK == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!free_resume) {
            free_maxchunks = 1;
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, free_resume, free_maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            handleServerErrors();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account, false);
        br.setFollowRedirects(false);
        DLLINK = null;
        getDllink_premium(link, br, true);
        if (cbr.containsHTML("\"BuyAdditionalTransfer")) {
            logger.info("Disabling chomikuj.pl account: Not enough traffic available");
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
        }
        if (DLLINK == null) {
            String argh1 = br.getRegex("orgFile\\\\\" value=\\\\\"(.*?)\\\\\"").getMatch(0);
            String argh2 = br.getRegex("userSelection\\\\\" value=\\\\\"(.*?)\\\\\"").getMatch(0);
            if (argh1 == null || argh2 == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            // For some files they ask
            // "Do you really want to download this file", so we have to confirm
            // it with "YES" here ;)
            if (cbr.containsHTML("Właściciel tego chomika udostępnia darmowy transfer, ale jego ilość jest obecnie zbyt mała, aby można było pobrać plik")) {
                br.postPage("http://chomikuj.pl/action/License/AcceptOwnTransfer?fileId=" + getFID(link), "orgFile=" + Encoding.urlEncode(argh1) + "&userSelection=" + Encoding.urlEncode(argh2) + "&__RequestVerificationToken=" + Encoding.urlEncode(link.getStringProperty("requestverificationtoken")));
            } else {
                br.postPage("http://chomikuj.pl/action/License/acceptLargeTransfer?fileId=" + getFID(link), "orgFile=" + Encoding.urlEncode(argh1) + "&userSelection=" + Encoding.urlEncode(argh2) + "&__RequestVerificationToken=" + Encoding.urlEncode(link.getStringProperty("requestverificationtoken")));
            }
            DLLINK = br.getRegex("redirectUrl\":\"(http://.*?)\"").getMatch(0);
            if (DLLINK == null) {
                DLLINK = br.getRegex("\\\\u003ca href=\\\\\"([^\"]*?)\\\\\" title").getMatch(0);
            }
            if (DLLINK != null) {
                DLLINK = Encoding.htmlDecode(DLLINK);
            }
            if (DLLINK == null) {
                getDllink(link, br, true);
            }
        }
        if (DLLINK == null) {
            logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        sleep(2 * 1000l, link);
        if (!account_resume) {
            account_maxchunks = 1;
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, DLLINK, account_resume, account_maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            handleServerErrors();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private void handleServerErrors() throws PluginException {
        if (br.getURL().contains("Error.aspx")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 15 * 60 * 1000l);
        }
    }

    private void login(Account account, boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                /** Load cookies */
                br.setCookiesExclusive(true);
                br.setFollowRedirects(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null && !force) {
                    this.br.setCookies(this.getHost(), cookies);
                    return;
                }
                getPage(this.br, MAINPAGE);
                final String lang = System.getProperty("user.language");
                final String requestVerificationToken = br.getRegex("<input name=\"__RequestVerificationToken\" type=\"hidden\" value=\"([^<>\"\\']+)\"").getMatch(0);
                if (requestVerificationToken == null) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                postPageRaw(this.br, "http://chomikuj.pl/action/Login/TopBarLogin", "rememberLogin=true&rememberLogin=false&topBar_LoginBtn=Zaloguj&ReturnUrl=%2F" + Encoding.urlEncode(account.getUser()) + "&Login=" + Encoding.urlEncode(account.getUser()) + "&Password=" + Encoding.urlEncode(account.getPass()) + "&__RequestVerificationToken=" + Encoding.urlEncode(requestVerificationToken));
                if (br.getCookie(MAINPAGE, "RememberMe") == null) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }

                br.setCookie(MAINPAGE, "cookiesAccepted", "1");
                br.setCookie(MAINPAGE, "spt", "0");
                br.setCookie(MAINPAGE, "rcid", "1");
                prepBR(this.br);

                postPageRaw(this.br, "http://chomikuj.pl/" + Encoding.urlEncode(account.getUser()), "ReturnUrl=%2F" + Encoding.urlEncode(account.getUser()) + "&Login=" + Encoding.urlEncode(account.getUser()) + "&Password=" + Encoding.urlEncode(account.getPass()) + "&rememberLogin=true&rememberLogin=false&topBar_LoginBtn=Zaloguj");
                getPage(this.br, "http://chomikuj.pl/" + Encoding.urlEncode(account.getUser()));

                account.saveCookies(this.br.getCookies(this.getHost()), "");
            } catch (final PluginException e) {
                account.clearCookies("");
                throw e;
            }
        }
    }

    private void getPage(final Browser br, final String url) throws Exception {
        br.getPage(url);
        cbr = br.cloneBrowser();
        cleanupBrowser(cbr, correctBR(br.toString()));
    }

    private void postPage(final Browser br, final String url, final String postData) throws Exception {
        br.postPage(url, postData);
        cbr = br.cloneBrowser();
        cleanupBrowser(cbr, correctBR(br.toString()));
    }

    private void postPageRaw(final Browser br, final String url, final String postData) throws Exception {
        br.postPageRaw(url, postData);
        cbr = br.cloneBrowser();
        cleanupBrowser(cbr, correctBR(br.toString()));
    }

    private void submitForm(final Browser br, final Form form) throws Exception {
        br.submitForm(form);
        cbr = br.cloneBrowser();
        cleanupBrowser(cbr, correctBR(br.toString()));
    }

    private String correctBR(final String input) {
        return input.replace("\\", "");
    }

    private void prepBR(final Browser br) {
        br.setAllowedResponseCodes(500);
    }

    /**
     * This allows backward compatibility for design flaw in setHtmlCode(), It injects updated html into all browsers that share the same
     * request id. This is needed as request.cloneRequest() was never fully implemented like browser.cloneBrowser().
     *
     * @param ibr
     *            Import Browser
     * @param t
     *            Provided replacement string output browser
     * @author raztoki
     * */
    private void cleanupBrowser(final Browser ibr, final String t) throws Exception {
        String dMD5 = JDHash.getMD5(ibr.toString());
        // preserve valuable original request components.
        final String oURL = ibr.getURL();
        final URLConnectionAdapter con = ibr.getRequest().getHttpConnection();

        Request req = new Request(oURL) {
            {
                boolean okay = false;
                try {
                    final Field field = this.getClass().getSuperclass().getDeclaredField("requested");
                    field.setAccessible(true);
                    field.setBoolean(this, true);
                    okay = true;
                } catch (final Throwable e2) {
                    e2.printStackTrace();
                }
                if (okay == false) {
                    try {
                        requested = true;
                    } catch (final Throwable e) {
                        e.printStackTrace();
                    }
                }

                httpConnection = con;
                setHtmlCode(t);
            }

            public long postRequest() throws IOException {
                return 0;
            }

            public void preRequest() throws IOException {
            }
        };

        ibr.setRequest(req);
        if (ibr.isDebug()) {
            logger.info("\r\ndirtyMD5sum = " + dMD5 + "\r\ncleanMD5sum = " + JDHash.getMD5(ibr.toString()) + "\r\n");
            System.out.println(ibr.toString());
        }
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ChoMikujPl.AVOIDPREMIUMMP3TRAFFICUSAGE, JDL.L("plugins.hoster.chomikujpl.avoidPremiumMp3TrafficUsage", "Force download of the stream versions of .mp3 files in account mode?\r\n<html><b>Avoids premium traffic usage for .mp3 files!</b></html>")).setDefaultValue(false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ChoMikujPl.DECRYPTFOLDERS, JDL.L("plugins.hoster.chomikujpl.decryptfolders", "Decrypt subfolders in folders")).setDefaultValue(true));
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}