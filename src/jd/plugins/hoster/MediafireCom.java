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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.InputField;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.UserAgents;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "mediafire.com" }, urls = { "https?://(www\\.)?mediafire\\.com/(download/[a-z0-9]+|(download\\.php\\?|\\?JDOWNLOADER(?!sharekey)|file/).*?(?=http:|$|\r|\n))" }, flags = { 32 })
public class MediafireCom extends PluginForHost {

    private static final boolean ACCOUNT_PREMIUM_RESUME          = true;
    private static final int     ACCOUNT_PREMIUM_MAXCHUNKS       = 0;
    private static final int     ACCOUNT_PREMIUM_MAXDOWNLOADS    = 20;

    private static final int     ACCOUNT_FREE_MAXDOWNLOADS       = 10;

    /** Settings stuff */
    private static final String  FREE_FORCE_RECONNECT_ON_CAPTCHA = "FREE_FORCE_RECONNECT_ON_CAPTCHA";

    public static String stringUserAgent() {
        return UserAgents.stringUserAgent();
    }

    public static String portableUserAgent() {
        return UserAgents.portableUserAgent();
    }

    public static String hbbtvUserAgent() {
        return UserAgents.hbbtvUserAgent();
    }

    /* End of HbbTV agents */

    /** end of random agents **/

    private static final String                              PRIVATEFILE           = JDL.L("plugins.hoster.mediafirecom.errors.privatefile", "Private file: Only downloadable for registered users");
    private static AtomicInteger                             maxPrem               = new AtomicInteger(1);
    private static final String                              PRIVATEFOLDERUSERTEXT = "This is a private folder. Re-Add this link while your account is active to make it work!";
    private String                                           SESSIONTOKEN          = null;
    private String                                           errorCode             = null;
    /* keep updated with decrypter */
    private final String                                     APPLICATIONID         = "27112";
    private final String                                     APIKEY                = "czQ1cDd5NWE3OTl2ZGNsZmpkd3Q1eXZhNHcxdzE4c2Zlbmt2djdudw==";
    /**
     * Map to cache the configuration keys
     */
    private static HashMap<Account, HashMap<String, String>> CONFIGURATION_KEYS    = new HashMap<Account, HashMap<String, String>>();

    public static abstract class PasswordSolver {

        protected Browser       br;
        protected PluginForHost plg;
        protected DownloadLink  dlink;
        private final int       maxTries;
        private int             currentTry;

        public PasswordSolver(final PluginForHost plg, final Browser br, final DownloadLink downloadLink) {
            this.plg = plg;
            this.br = br;
            this.dlink = downloadLink;
            this.maxTries = 3;
            this.currentTry = 0;
        }

        abstract protected void handlePassword(String password) throws Exception;

        // do not add @Override here to keep 0.* compatibility
        public boolean hasAutoCaptcha() {
            return false;
        }

        abstract protected boolean isCorrect();

        public void run() throws Exception {
            while (this.currentTry++ < this.maxTries) {
                String password = null;
                if ((password = this.dlink.getStringProperty("pass", null)) != null) {
                } else {
                    password = Plugin.getUserInput(JDL.LF("PasswordSolver.askdialog", "Downloadpassword for %s/%s", this.plg.getHost(), this.dlink.getName()), this.dlink);
                }
                if (password == null) {
                    throw new PluginException(LinkStatus.ERROR_FATAL, JDL.L("plugins.errors.wrongpassword", "Password wrong"));
                }
                this.handlePassword(password);
                if (!this.isCorrect()) {
                    this.dlink.setProperty("pass", Property.NULL);
                    continue;
                } else {
                    this.dlink.setProperty("pass", password);
                    return;
                }

            }
            throw new PluginException(LinkStatus.ERROR_RETRY, JDL.L("plugins.errors.wrongpassword", "Password wrong"));
        }
    }

    private static AtomicReference<String> agent                      = new AtomicReference<String>(stringUserAgent());

    static private final String            offlinelink                = "tos_aup_violation";

    /** The name of the error page used by MediaFire */
    private static final String            ERROR_PAGE                 = "error.php";
    /**
     * The number of retries to be performed in order to determine if a file is availableor to try captcha/password.
     */
    private int                            max_number_of_free_retries = 3;

    private String                         fileID;

    private String                         dlURL;

    @SuppressWarnings("deprecation")
    public MediafireCom(final PluginWrapper wrapper) {
        super(wrapper);
        this.setStartIntervall(5000);
        this.enablePremium("https://www.mediafire.com/upgrade/");
        setConfigElements();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void correctDownloadLink(final DownloadLink link) throws Exception {
        final String id = new Regex(link.getDownloadURL(), "mediafire\\.com/download/([a-z0-9]+)").getMatch(0);
        if (id != null) {
            link.setProperty("LINKDUPEID", "mediafirecom_" + id);
        }
        link.setUrlDownload(link.getDownloadURL().replaceFirst("http://media", "http://www.media"));
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        // old setter - remove after next stable update 20130918
        account.setProperty("freeaccount", Property.NULL);

        final AccountInfo ai = new AccountInfo();
        /* reset maxPrem workaround on every fetchaccount info */
        maxPrem.set(1);
        try {
            login(br, account, true);
        } catch (final PluginException e) {
            account.setValid(false);
            throw e;
        }
        account.setValid(true);
        if (account.getBooleanProperty("free")) {
            ai.setStatus("Registered (free) User");
            ai.setUnlimitedTraffic();
            try {
                maxPrem.set(ACCOUNT_FREE_MAXDOWNLOADS);
                account.setMaxSimultanDownloads(ACCOUNT_FREE_MAXDOWNLOADS);
                account.setConcurrentUsePossible(true);
            } catch (final Throwable e) {
            }
        } else {
            br.setFollowRedirects(true);
            br.getPage("/myaccount/");
            String trafficleft = br.getRegex("View Statistics</a></span>.+</div>.+class=\"lg-txt\">([^<>]+)</div>").getMatch(0);
            if (trafficleft != null) {
                trafficleft = trafficleft.trim();
                if (Regex.matches(trafficleft, Pattern.compile("(tb|tbyte|terabyte|tib)", Pattern.CASE_INSENSITIVE))) {
                    String[] trafficleftArray = trafficleft.split(" ");
                    double trafficsize = Double.parseDouble(trafficleftArray[0]);
                    trafficsize *= 1024;
                    trafficleft = Double.toString(trafficsize) + " GB";
                }
                ai.setTrafficLeft(SizeFormatter.getSize(trafficleft));
            }
            ai.setStatus("Premium User");
            try {
                maxPrem.set(ACCOUNT_PREMIUM_MAXDOWNLOADS);
                account.setMaxSimultanDownloads(ACCOUNT_PREMIUM_MAXDOWNLOADS);
                account.setConcurrentUsePossible(true);
            } catch (final Throwable e) {
            }
        }
        return ai;
    }

    @Override
    public String getAGBLink() {
        return "http://www.mediafire.com/terms_of_service.php";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 10;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /* workaround for free/premium issue on stable 09581 */
        return maxPrem.get();
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (downloadLink.getBooleanProperty("privatefolder")) {
            throw new PluginException(LinkStatus.ERROR_FATAL, PRIVATEFOLDERUSERTEXT);
        }
        doFree(downloadLink, null);
    }

    @SuppressWarnings("deprecation")
    public void doFree(final DownloadLink downloadLink, final Account account) throws Exception {
        String url = null;
        int trycounter = 0;
        boolean captchaCorrect = false;
        if (account == null) {
            this.br.getHeaders().put("User-Agent", MediafireCom.agent.get());
        }
        do {
            if (url != null) {
                break;
            }
            this.requestFileInformation(downloadLink);
            if (downloadLink.getBooleanProperty("privatefile") && account == null) {
                throw new PluginException(LinkStatus.ERROR_FATAL, PRIVATEFILE);
            }
            // Check for direct link
            try {
                br.setFollowRedirects(true);
                URLConnectionAdapter con = null;
                try {
                    con = br.openGetConnection(downloadLink.getDownloadURL());
                    if (!con.getContentType().contains("html")) {
                        url = downloadLink.getDownloadURL();
                    } else {
                        br.followConnection();
                    }
                } finally {
                    try {
                        con.disconnect();
                    } catch (Throwable e) {
                    }
                }
                handleNonAPIErrors(downloadLink, br);
                if (url == null) {
                    // TODO: This errorhandling is missing for premium users!
                    captchaCorrect = false;
                    Form form = br.getFormbyProperty("name", "form_captcha");
                    String freeArea = br.getRegex("class=\"nonOwner nonpro_adslayout dl\\-page dlCaptchaActive\"(.*?)class=\"captchaPromo\"").getMatch(0);
                    if (freeArea == null) {
                        freeArea = br.getRegex("class=\"nonOwner nonpro_adslayout dl\\-page dlCaptchaActive\"(.*?)class=\"dl\\-utility\\-nav\"").getMatch(0);
                    }
                    if (freeArea != null && freeArea.contains("solvemedia.com/papi/")) {
                        logger.info("Detected captcha method \"solvemedia\" for this host");
                        handleExtraReconnectSettingOnCaptcha(account);

                        final org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia sm = new org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia(br);
                        final File cf = sm.downloadCaptcha(getLocalCaptchaFile());
                        String code = getCaptchaCode(cf, downloadLink);
                        String chid = sm.getChallenge(code);
                        form.put("adcopy_challenge", chid);
                        form.put("adcopy_response", code.replace(" ", "+"));
                        br.submitForm(form);
                        if (br.getFormbyProperty("name", "form_captcha") != null) {
                            logger.info("solvemedia captcha wrong");
                            continue;
                        }
                    } else if (freeArea != null && new Regex(freeArea, "(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)").matches()) {
                        logger.info("Detected captcha method \"Re Captcha\" for this host");
                        handleExtraReconnectSettingOnCaptcha(account);
                        final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                        final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(this.br);
                        String id = new Regex(freeArea, "challenge\\?k=(.+?)\"").getMatch(0);
                        if (id != null) {
                            logger.info("CaptchaID found, Form found " + (form != null));
                            rc.setId(id);
                            final InputField challenge = new InputField("recaptcha_challenge_field", null);
                            final InputField code = new InputField("recaptcha_response_field", null);
                            form.addInputField(challenge);
                            form.addInputField(code);
                            rc.setForm(form);
                            rc.load();
                            final File cf = rc.downloadCaptcha(this.getLocalCaptchaFile());
                            boolean defect = false;
                            try {
                                final String c = this.getCaptchaCode("recaptcha", cf, downloadLink);
                                rc.setCode(c);
                                form = br.getFormbyProperty("name", "form_captcha");
                                id = br.getRegex("challenge\\?k=(.+?)\"").getMatch(0);
                                if (form != null && id == null) {
                                    logger.info("Form found but no ID");
                                    defect = true;
                                    logger.info("PluginError 672");
                                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                                }
                                if (id != null) {
                                    /* captcha wrong */
                                    logger.info("reCaptcha captcha wrong");
                                    continue;
                                }
                            } catch (final PluginException e) {
                                if (defect) {
                                    throw e;
                                }
                                /**
                                 * captcha input timeout run out.. try to reconnect
                                 */
                                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Try reconnect to avoid more captchas", 5 * 60 * 1000l);
                            }
                        }
                    } else if (freeArea != null && freeArea.contains("g-recaptcha-response")) {
                        handleExtraReconnectSettingOnCaptcha(account);
                        final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
                        form.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                        br.submitForm(form);
                    } else if (freeArea != null && freeArea.contains("for=\"customCaptchaCheckbox\"")) {
                        /* Mediafire custom checkbox "captcha" */
                        form.put("mf_captcha_response", "1");
                        br.submitForm(form);
                    }
                }
            } catch (final Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                if (e instanceof PluginException) {
                    throw (PluginException) e;
                }
            }
            captchaCorrect = true;
            if (url == null) {
                logger.info("Handle possible PW");
                this.handlePW(downloadLink);
                url = br.getRegex("kNO = \"(http://.*?)\"").getMatch(0);
                logger.info("Kno= " + url);
                if (url == null) {
                    /* pw protected files can directly redirect to download */
                    url = br.getRedirectLocation();
                }
            }
            trycounter++;
        } while (trycounter < max_number_of_free_retries && url == null);
        if (url == null) {
            if (!captchaCorrect) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            logger.info("PluginError 721");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        this.br.setFollowRedirects(true);
        this.br.setDebug(true);
        this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, url, true, 0);
        if (!this.dl.getConnection().isContentDisposition()) {
            handleServerErrors();
            logger.info("Error (3)");
            logger.info(dl.getConnection() + "");
            this.br.followConnection();
            handleNonAPIErrors(downloadLink, this.br);
            if (br.containsHTML("We apologize, but we are having difficulties processing your download request")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Please be patient while we try to repair your download request", 2 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        this.dl.startDownload();
    }

    private void handleServerErrors() throws PluginException {
        if (dl.getConnection().getResponseCode() == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403, ", 30 * 60 * 1000l);
        } else if (dl.getConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404, ", 30 * 60 * 1000l);
        }
    }

    private void handleExtraReconnectSettingOnCaptcha(final Account account) throws PluginException {
        if (this.getPluginConfig().getBooleanProperty(FREE_FORCE_RECONNECT_ON_CAPTCHA, false)) {
            if (account != null) {
                logger.info("Captcha reconnect setting active & free account used --> TEMPORARILY_UNAVAILABLE");
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Waiting some time to avoid captcha in free account mode", 30 * 60 * 1000l);
            } else {
                logger.info("Captcha reconnect setting active & NO account used --> IP_BLOCKED");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Reconnecting or waiting some time to avoid captcha in free mode", 30 * 60 * 1000l);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private String getID(DownloadLink link) {
        String fileID = new Regex(link.getDownloadURL(), "\\?([a-zA-Z0-9]+)").getMatch(0);
        if (fileID == null) {
            fileID = new Regex(link.getDownloadURL(), "(file|download)/([a-zA-Z0-9]+)").getMatch(1);
        }
        return fileID;
    }

    @Override
    public void handlePremium(final DownloadLink downloadLink, final Account account) throws Exception {
        requestFileInformation(downloadLink);
        if (downloadLink.getBooleanProperty("privatefolder")) {
            throw new PluginException(LinkStatus.ERROR_FATAL, PRIVATEFOLDERUSERTEXT);
        }
        login(br, account, false);
        if (account.getBooleanProperty("free", false)) {
            doFree(downloadLink, account);
        } else {
            // TODO: See if there is a way to implement the premium API again: http://developers.mediafire.com/index.php/REST_API
            /**
             * Problem: This API doesn't (yet) work with password protected links...
             */
            // getSessionToken(this.br, account);
            // apiRequest(this.br, "http://www.mediafire.com/api/file/get_links.php", "?link_type=direct_download&session_token=" +
            // this.SESSIONTOKEN + "&quick_key=" + getFID(downloadLink) + "&response_format=json");
            String url = dlURL;
            boolean passwordprotected = false;
            boolean useAPI = false;
            // the below if statement is always false by the above: useAPI = false
            if (url == null && useAPI) {
                this.fileID = getID(downloadLink);
                this.br.postPageRaw("http://www.mediafire.com/basicapi/premiumapi.php", "premium_key=" + MediafireCom.CONFIGURATION_KEYS.get(account) + "&files=" + this.fileID);
                url = this.br.getRegex("<url>(http.*?)</url>").getMatch(0);
                if ("-202".equals(this.br.getRegex("<flags>(.*?)</").getMatch(0))) {
                    br.setFollowRedirects(false);
                    br.getPage("http://www.mediafire.com/?" + fileID);
                    url = br.getRedirectLocation();
                    if (url == null || !url.contains("download")) {
                        this.handlePW(downloadLink);
                        url = br.getRedirectLocation();
                    }
                    if (url == null) {
                        throw new PluginException(LinkStatus.ERROR_FATAL, "Private file. No Download possible");
                    }
                }
                if ("-204".equals(this.br.getRegex("<flags>(.*?)</").getMatch(0))) {
                    passwordprotected = true;
                    new PasswordSolver(this, this.br, downloadLink) {

                        @Override
                        protected void handlePassword(final String password) throws Exception {
                            this.br.postPageRaw("http://www.mediafire.com/basicapi/premiumapi.php", "file_1=" + MediafireCom.this.fileID + "&password_1=" + password + "&premium_key=" + MediafireCom.CONFIGURATION_KEYS.get(account) + "&files=" + MediafireCom.this.fileID);

                        }

                        @Override
                        protected boolean isCorrect() {
                            return this.br.getRegex("<url>(http.*?)</url>").getMatch(0) != null;
                        }

                    }.run();

                    url = this.br.getRegex("<url>(http.*?)</url>").getMatch(0);

                }
                if ("-105".equals(this.br.getRegex("<flags>(.*?)</").getMatch(0))) {
                    logger.info("Insufficient bandwidth");
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
                }
            } else if (url == null && useAPI == false) {
                this.fileID = getID(downloadLink);
                br.setFollowRedirects(false);
                br.getPage("http://www.mediafire.com/download/" + fileID);
                /* url should be downloadlink when directDownload is enabled */
                url = getURL(br);
                if (url == null) {
                    handleNonAPIErrors(downloadLink, br);
                }
            }
            if (url == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }

            URLConnectionAdapter con = null;
            try {
                con = br.openGetConnection(url);
                if (con.getContentType().contains("html")) {
                    handleNonAPIErrors(downloadLink, this.br);
                    this.br.followConnection();
                    if (br.containsHTML("class=\"downloadpassword\"")) {
                        this.handlePremiumPassword(downloadLink, account);
                        return;
                    }
                    url = br.getRegex("kNO = \"(http://[^<>\"]*?)\"").getMatch(0);
                    if (url == null) {
                        lastChangeErrorhandling(downloadLink, account, passwordprotected);
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }

            this.br.setFollowRedirects(true);
            this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, url, ACCOUNT_PREMIUM_RESUME, ACCOUNT_PREMIUM_MAXCHUNKS);
            if (!this.dl.getConnection().isContentDisposition()) {
                handleServerErrors();
                logger.info("Error (4)");
                logger.info(dl.getConnection() + "");
                this.br.followConnection();
                handleNonAPIErrors(downloadLink, this.br);
                lastChangeErrorhandling(downloadLink, account, passwordprotected);
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            this.dl.startDownload();
        }
    }

    private void lastChangeErrorhandling(final DownloadLink downloadLink, final Account account, final boolean passwordprotected) throws Exception {
        if (this.br.getRequest().getHttpConnection().getResponseCode() == 403) {
            logger.info("Error (3)");
        } else if (this.br.getRequest().getHttpConnection().getResponseCode() == 200 && passwordprotected) {
            // workaround for api error: try website password solving
            this.handlePremiumPassword(downloadLink, account);
            return;
        }
    }

    private void handlePremiumPassword(final DownloadLink downloadLink, final Account account) throws Exception {
        String url = null;
        if (br.getURL().contains("/download/")) {
            url = br.getURL();
        } else {
            br.getPage("http://www.mediafire.com/download/" + fileID);
            url = br.getRedirectLocation();
            if (url != null) {
                br.getPage(url);
            }
        }
        this.handlePW(downloadLink);
        url = getURL(br);
        if (url == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        this.br.setFollowRedirects(true);
        this.dl = jd.plugins.BrowserAdapter.openDownload(this.br, downloadLink, url, ACCOUNT_PREMIUM_RESUME, ACCOUNT_PREMIUM_MAXCHUNKS);

        if (!this.dl.getConnection().isContentDisposition()) {
            logger.info("Error (3)");
            logger.info(dl.getConnection() + "");
            this.br.followConnection();
            if (br.containsHTML("class=\"downloadpassword\"")) {
                throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        this.dl.startDownload();
    }

    private String getURL(Browser br) throws IOException {
        String url = br.getRedirectLocation();
        if (url == null) {
            url = br.getRegex("kNO = \"(http://.*?)\"").getMatch(0);
        }
        if (url == null) {
            /* try the same */
            Browser brc = br.cloneBrowser();
            brc.getPage("http://www.mediafire.com/dynamic/dlget.php?qk=" + fileID);
            url = brc.getRegex("dllink\":\"(http:.*?)\"").getMatch(0);
            if (url != null) {
                url = url.replaceAll("\\\\", "");
            }
        }
        return url;
    }

    private void handlePW(final DownloadLink downloadLink) throws Exception {
        if (this.br.containsHTML("dh\\(''\\)")) {
            new PasswordSolver(this, this.br, downloadLink) {
                String curPw = null;

                @Override
                protected void handlePassword(final String password) throws Exception {
                    curPw = password;
                    final Form form = this.br.getFormbyProperty("name", "form_password");
                    if (form == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    form.put("downloadp", Encoding.urlEncode(curPw));
                    this.br.submitForm(form);
                }

                @Override
                protected boolean isCorrect() {
                    Form form = this.br.getFormbyProperty("name", "form_password");
                    if (form != null) {
                        return false;
                    } else {
                        return true;
                    }
                }

            }.run();
        }

    }

    @Override
    public void init() {
        Browser.setRequestIntervalLimitGlobal(this.getHost(), 250);
    }

    public void login(final Browser lbr, final Account account, boolean force) throws Exception {
        boolean red = lbr.isFollowingRedirects();
        synchronized (CONFIGURATION_KEYS) {
            try {
                HashMap<String, String> cookies = null;
                if (force == false && (cookies = MediafireCom.CONFIGURATION_KEYS.get(account)) != null) {
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            lbr.setCookie("http://www.mediafire.com/", key, value);
                        }
                        return;
                    }
                }
                this.setBrowserExclusive();
                final String lang = System.getProperty("user.language");
                lbr.setFollowRedirects(true);
                if (!isValidMailAdress(account.getUser())) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBitte trage deine E-Mail Adresse in das 'Name'-Feld ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlease enter your e-mail adress in the 'Name'-field.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                lbr.getPage("http://www.mediafire.com/");
                Form form = lbr.getFormbyProperty("name", "form_login1");
                if (form == null) {
                    form = lbr.getFormBySubmitvalue("login_email");
                }
                if (form == null) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                form.put("login_email", Encoding.urlEncode(account.getUser()));
                form.put("login_pass", Encoding.urlEncode(account.getPass()));
                lbr.submitForm(form);
                lbr.getPage("/myfiles.php");
                final String cookie = lbr.getCookie("http://www.mediafire.com", "user");
                if ("x".equals(cookie) || cookie == null) {
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                lbr.setFollowRedirects(false);
                lbr.getPage("https://www.mediafire.com/myaccount/download_options.php");
                if (lbr.getRedirectLocation() != null && lbr.getRedirectLocation().contains("mediafire.com/upgrade")) {
                    account.setProperty("free", true);
                } else {
                    account.setProperty("free", false);
                    String di = lbr.getRegex("di='(.*?)'").getMatch(0);
                    lbr.getPage("/dynamic/download_options.php?enable_me_from_me=0&nocache=" + new Random().nextInt(1000) + "&di=" + di);
                    // String configurationKey = getAPIKEY(lbr);
                    // if (configurationKey == null) throw new PluginException(LinkStatus.ERROR_PREMIUM,
                    // PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                cookies = new HashMap<String, String>();
                final Cookies add = lbr.getCookies("http://www.mediafire.com");
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                MediafireCom.CONFIGURATION_KEYS.put(account, cookies);
            } catch (final PluginException e) {
                MediafireCom.CONFIGURATION_KEYS.remove(account);
                throw e;
            } finally {
                lbr.setFollowRedirects(red);
            }
        }
    }

    // private String getAPIKEY(Browser br) {
    // if (br == null) return null;
    // String configurationKey = this.br.getRegex("Configuration Key:.*? value=\"(.*?)\"").getMatch(0);
    // if (configurationKey == null) configurationKey = this.br.getRegex("Configuration Key.*? value=\"(.*?)\"").getMatch(0);
    // return configurationKey;
    // }
    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException, InterruptedException {
        br.setFollowRedirects(false);
        br.setCustomCharset("utf-8");

        downloadLink.setProperty("type", Property.NULL);
        if (downloadLink.getBooleanProperty("offline")) {
            return AvailableStatus.FALSE;
        }
        final String fid = getFID(downloadLink);
        if (downloadLink.getBooleanProperty("privatefolder")) {
            downloadLink.getLinkStatus().setStatusText(PRIVATEFOLDERUSERTEXT);
            downloadLink.setName(fid);
            return AvailableStatus.TRUE;
        }
        downloadLink.setLinkID(fid);
        final Browser apiBR = br.cloneBrowser();
        final Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa != null) {
            getSessionToken(apiBR, aa);
        }
        apiRequest(apiBR, "http://www.mediafire.com/api/file/get_info.php", "?quick_key=" + fid);
        if ("114".equals(errorCode)) {
            downloadLink.setProperty("privatefile", true);
            return AvailableStatus.TRUE;
        }

        if ("110".equals(errorCode)) {
            // <response><action>file/get_info</action><message>Unknown or Invalid
            // QuickKey</message><error>110</error><result>Error</result><current_api_version>2.15</current_api_version></response>
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        downloadLink.setDownloadSize(SizeFormatter.getSize(getXML("size", apiBR.toString() + "b")));
        // stable has issues with utf-8 filenames provided from Content-Disposition, even when customcharset is used..
        downloadLink.setFinalFileName(Encoding.htmlDecode(getXML("filename", apiBR.toString())));
        return AvailableStatus.TRUE;
    }

    private String getXML(final String parameter, final String source) {
        return new Regex(source, "<" + parameter + ">([^<>\"]*?)</" + parameter + ">").getMatch(0);
    }

    private void apiRequest(final Browser br, final String url, final String data) throws IOException {
        if (SESSIONTOKEN == null) {
            br.getPage(url + data);
        } else {
            br.getPage(url + data + "&session_token=" + SESSIONTOKEN);
        }
        errorCode = getXML("error", br.toString());
    }

    private void getSessionToken(final Browser apiBR, final Account aa) throws IOException {
        // Try to re-use session token as long as possible (it's valid for 10 minutes)
        final String savedusername = this.getPluginConfig().getStringProperty("username");
        final String savedpassword = this.getPluginConfig().getStringProperty("password");
        final String sessiontokenCreateDateObject = this.getPluginConfig().getStringProperty("sessiontokencreated2");
        long sessiontokenCreateDate = -1;
        if (sessiontokenCreateDateObject != null && sessiontokenCreateDateObject.length() > 0) {
            sessiontokenCreateDate = Long.parseLong(sessiontokenCreateDateObject);
        }
        if ((savedusername != null && savedusername.matches(aa.getUser())) && (savedpassword != null && savedpassword.matches(aa.getPass())) && System.currentTimeMillis() - sessiontokenCreateDate < 600000) {
            SESSIONTOKEN = this.getPluginConfig().getStringProperty("sessiontoken");
        } else {
            // Get token for user account
            apiRequest(apiBR, "https://www.mediafire.com/api/user/get_session_token.php", "?email=" + Encoding.urlEncode(aa.getUser()) + "&password=" + Encoding.urlEncode(aa.getPass()) + "&application_id=" + APPLICATIONID + "&signature=" + JDHash.getSHA1(aa.getUser() + aa.getPass() + APPLICATIONID + Encoding.Base64Decode(APIKEY)) + "&version=1");
            SESSIONTOKEN = getXML("session_token", apiBR.toString());
            this.getPluginConfig().setProperty("username", aa.getUser());
            this.getPluginConfig().setProperty("password", aa.getPass());
            this.getPluginConfig().setProperty("sessiontoken", SESSIONTOKEN);
            this.getPluginConfig().setProperty("sessiontokencreated2", "" + System.currentTimeMillis());
            this.getPluginConfig().save();
        }
    }

    private String getFID(final DownloadLink downloadLink) {
        // http://www.mediafire.com/file/dyvzdsdsasdg1y4d/myfile format
        String id = new Regex(downloadLink.getDownloadURL(), "file/([a-z0-9]+)/").getMatch(0);
        if (id != null) {
            return id;
        }
        return new Regex(downloadLink.getDownloadURL(), "([a-z0-9]+)$").getMatch(0);
    }

    private void handleNonAPIErrors(final DownloadLink dl, Browser imported) throws PluginException, IOException {
        // imported browser affects this.br so lets make a new browser just for error checking.
        Browser eBr = new Browser();
        // catch, and prevent a null imported browser
        if (imported == null) {
            imported = this.br.cloneBrowser();
        }
        if (imported != null) {
            eBr = imported.cloneBrowser();
        } else {
            // prob not required...
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        // Some errors are only provided if isFollowingRedirects==true. As this isn't always the case throughout the plugin, lets grab the
        // redirect page so we can use .containsHTML
        if (!eBr.isFollowingRedirects()) {
            if (eBr.getRedirectLocation() != null) {
                eBr.getPage(eBr.getRedirectLocation());
            }
        }

        // error checking below!
        if (eBr.getURL().matches(".+/error\\.php\\?errno=3(20|78|80|88).*?")) {
            // 320 = file is removed by the originating user or MediaFire.
            // 378 = File Removed for Violation (of TOS)
            // 380 = claimed by a copyright holder through a valid DMCA request
            // 388 = identified as copyrighted work
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (eBr.getURL().matches(".+/error\\.php\\?errno=394.*?")) {
            /*
             * The file you attempted to download is an archive that is encrypted or password protected. MediaFire does not support
             * unlimited downloads of encrypted or password protected archives and the limit for this file has been reached. MediaFire
             * understands the need for users to transfer encrypted and secured files, we offer this service starting at $1.50 per month. We
             * have informed the owner that sharing of this file has been limited and how they can resolve this issue.
             */
            throw new PluginException(LinkStatus.ERROR_FATAL, "Download not possible, retriction based on uploaders account");
        } else if (eBr.getURL().contains("mediafire.com/error.php?errno=382")) {
            dl.getLinkStatus().setStatusText("File Belongs to Suspended Account.");
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (eBr.containsHTML("class=\"error\\-title\">Temporarily Unavailable</p>")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "This file is temporarily unavailable!", 30 * 60 * 1000l);
        } else if (eBr.containsHTML("class=\"error-title\">This download is currently unavailable<")) {
            // jdlog://7235652095341
            final String time = eBr.getRegex("we will retry your download again in (\\d+) seconds\\.<").getMatch(0);
            long t = ((time != null ? Long.parseLong(time) : 60) * 1000l) + 2;
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "This file is temporarily unavailable!", t);
        }
    }

    private boolean isValidMailAdress(final String value) {
        return value.matches(".+@.+");
    }

    @Override
    public String getDescription() {
        return "JDownloader's mediafire.com plugin helps downloading files from mediafire.com.";
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), FREE_FORCE_RECONNECT_ON_CAPTCHA, JDL.L("plugins.hoster.MediafireCom.FreeForceReconnectOnCaptcha", "Free mode: Reconnect if captcha input needed?\r\n<html><p style=\"color:#F62817\"><b>WARNING: This setting can prevent captchas but it can also lead to an infinite reconnect loop!</b></p></html>")).setDefaultValue(false));
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
        link.setProperty("type", Property.NULL);
    }

    @Override
    public void resetPluginGlobals() {
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
        return false;
    }
}