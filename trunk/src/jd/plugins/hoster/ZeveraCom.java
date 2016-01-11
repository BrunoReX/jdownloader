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

import java.lang.reflect.Field;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.config.Property;
import jd.controlling.AccountController;
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
import jd.plugins.PluginException;
import jd.plugins.hoster.PremiumaxNet.UnavailableHost;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "zevera.com" }, urls = { "https?://\\w+\\.zevera\\.com/getFiles\\.as(p|h)x\\?ourl=.+" }, flags = { 2 })
public class ZeveraCom extends antiDDoSForHost {

    // DEV NOTES
    // supports last09 based on pre-generated links and jd2
    /* Important - all of these belong together: zevera.com, multihosters.com, putdrive.com(?!) */

    private static final String                                       mName              = "zevera.com";
    private static final String                                       NICE_HOSTproperty  = mName.replaceAll("(\\.|\\-)", "");
    private static final String                                       mProt              = "http://";
    private static final String                                       mServ              = mProt + "api." + mName;
    private static Object                                             LOCK               = new Object();
    private static HashMap<Account, HashMap<String, UnavailableHost>> hostUnavailableMap = new HashMap<Account, HashMap<String, UnavailableHost>>();

    private static final String                                       NOCHUNKS           = "NOCHUNKS";
    private Account                                                   currAcc            = null;
    private DownloadLink                                              currDownloadLink   = null;

    public ZeveraCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(mProt + mName + "/");
    }

    @Override
    public String getAGBLink() {
        return mProt + mName + "/terms";
    }

    @Override
    protected Browser prepBrowser(final Browser prepBr, final String host) {
        super.prepBrowser(prepBr, host);
        // define custom browser headers and language settings.
        prepBr.setCookie(mProt + mName, "lang", "english");
        // prepBr.getHeaders().put("User-Agent", "JDownloader");
        prepBr.setCustomCharset("utf-8");
        prepBr.setConnectTimeout(60 * 1000);
        prepBr.setReadTimeout(60 * 1000);
        return prepBr;
    }

    /**
     * JD 2 Code. DO NOT USE OVERRIDE FOR COMPATIBILITY REASONS
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    public boolean checkLinks(DownloadLink[] urls) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        try {
            List<Account> accs = AccountController.getInstance().getValidAccounts(this.getHost());
            if (accs == null || accs.size() == 0) {
                logger.info("No account present, Please add a premium" + mName + "account.");
                for (DownloadLink dl : urls) {
                    /* no check possible */
                    dl.setAvailableStatus(AvailableStatus.UNCHECKABLE);
                }
                return false;
            }
            login(accs.get(0), false);
            br.setFollowRedirects(true);
            for (DownloadLink dl : urls) {
                URLConnectionAdapter con = null;
                try {
                    con = openAntiDDoSRequestConnection(br, br.createGetRequest(dl.getDownloadURL()));
                    if (con.isContentDisposition()) {
                        dl.setFinalFileName(getFileNameFromHeader(con));
                        dl.setDownloadSize(con.getLongContentLength());
                        dl.setAvailable(true);
                    } else {
                        dl.setAvailable(false);
                    }
                } finally {
                    try {
                        /* make sure we close connection */
                        con.disconnect();
                    } catch (final Throwable e) {
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws PluginException {
        checkLinks(new DownloadLink[] { link });
        if (!link.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return getAvailableStatus(link);
    }

    private AvailableStatus getAvailableStatus(DownloadLink link) {
        try {
            final Field field = link.getClass().getDeclaredField("availableStatus");
            field.setAccessible(true);
            Object ret = field.get(link);
            if (ret != null && ret instanceof AvailableStatus) {
                return (AvailableStatus) ret;
            }
        } catch (final Throwable e) {
        }
        return AvailableStatus.UNCHECKED;
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) {
        if (account == null) {
            /* without account its not possible to download the link */
            return false;
        }
        return true;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, "Download only works with a premium" + mName + "account.", PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        setConstants(account, link);
        login(account, false);
        showMessage(link, "Phase 1/3: URL check for pre-generated links!");
        requestFileInformation(link);
        showMessage(link, "Pgase 2/3: Download ready!");
        handleDL(link, link.getDownloadURL());
    }

    private void setConstants(final Account acc, final DownloadLink dl) {
        this.currAcc = acc;
        this.currDownloadLink = dl;
    }

    private void handleDL(final DownloadLink link, String dllink) throws Exception {
        // Zevera uses this redirect logic to wait for the actual file in the backend. This means: follow the redirects until we get Data!
        // After 10 redirects Zevera shows an error. We do not allow more than 10 redirects - so we probably never see this error page
        //
        // Besides redirects, the connections often run into socket exceptions. do the same on socket problems - retry
        // according to Zevera, 20 retries should be enough

        br.setFollowRedirects(true);
        showMessage(link, "Phase 3/3: Check download!");
        int maxchunks = 0;
        if (link.getBooleanProperty(ZeveraCom.NOCHUNKS, false)) {
            maxchunks = 1;
        }
        try {
            logger.info("Connecting to " + new URL(dllink).getHost());
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, maxchunks);
        } catch (final PluginException e) {
            if ("Redirectloop".equals(e.getErrorMessage())) {
                logger.info("zevera.com: Download failed because of a Redirectloop -> This is caused by zevera and NOT a JD issue!");
                handleErrorRetries("redirectloop", 20, 2 * 60 * 1000l);
            }
            /* unknown error, we disable multiple chunks */
            if (link.getBooleanProperty(ZeveraCom.NOCHUNKS, false) == false) {
                link.setProperty(ZeveraCom.NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            logger.info("Zevera download failed because: " + e.getMessage());
            logger.info("Zevera.com: Name of the errorMessage: " + e.getErrorMessage());
            throw e;
        } catch (final SocketTimeoutException e) {
            logger.info("zevera.com: Download failed because of a timeout -> This is caused by zevera and NOT a JD issue!");
            handleErrorRetries("timeout", 20, 5 * 60 * 1000l);
        } catch (final SocketException e) {
            logger.info("Zevera download failed because of a timeout/connection problem -> This is probably caused by zevera and NOT a JD issue!");
            handleErrorRetries("timeout", 20, 5 * 60 * 1000l);
        } catch (final BrowserException e) {
            // some exemptions happen in browser exceptions and not collected by sockettimeoutexception/socketexception
            logger.info("Zevera download failed because of a timeout/connection problem -> This is probably caused by zevera and NOT a JD issue!");
            getLogger().log(e);
            handleErrorRetries("BrowserException", 20, 5 * 60 * 1000l);
        } catch (final Exception e) {
            logger.info("Zevera download FATAL failed because: " + e.getMessage());
            throw e;
        }
        if (dl.getConnection().getResponseCode() == 404) {
            handleErrorRetries("servererror404", 20, 2 * 60 * 1000l);
        }
        if (!dl.getConnection().getContentType().contains("html")) {
            /* contentdisposition, lets download it */
            try {
                if (!this.dl.startDownload()) {
                    try {
                        if (dl.externalDownloadStop()) {
                            return;
                        }
                    } catch (final Throwable e) {
                    }
                    /* unknown error, we disable multiple chunks */
                    if (link.getBooleanProperty(ZeveraCom.NOCHUNKS, false) == false) {
                        link.setProperty(ZeveraCom.NOCHUNKS, Boolean.valueOf(true));
                        throw new PluginException(LinkStatus.ERROR_RETRY);
                    }
                }
            } catch (final PluginException e) {
                // New V2 errorhandling
                /* unknown error, we disable multiple chunks */
                if (e.getLinkStatus() != LinkStatus.ERROR_RETRY && link.getBooleanProperty(ZeveraCom.NOCHUNKS, false) == false) {
                    link.setProperty(ZeveraCom.NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                } else {
                    throw e;
                }
            }
            return;
        } else {
            /*
             * download is not contentdisposition, so remove this host from premiumHosts list
             */
            if (dl.getConnection().getResponseCode() == 500) {
                handleErrorRetries("servererror500", 20, 5 * 60 * 1000l);
            }
            br.followConnection();
        }
        try {
            handleErrorRetries("unknowndlerroratend", 50, 10 * 60 * 1000l);
        } catch (final Throwable xxe) {
            if (xxe instanceof PluginException && ((PluginException) xxe).getLinkStatus() != LinkStatus.ERROR_RETRY) {
                /* Finally, failed! */
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
    }

    /**
     * Is intended to handle out of date errors which might occur seldom by re-tring a couple of times before we temporarily remove the host
     * from the host list.
     *
     * @param error
     *            : The name of the error
     * @param maxRetries
     *            : Max retries before out of date error is thrown
     */
    private void handleErrorRetries(final String error, final int maxRetries, final long timeout) throws PluginException {
        int timesFailed = this.currDownloadLink.getIntegerProperty(NICE_HOSTproperty + "failedtimes_" + error, 0);
        if (timesFailed <= maxRetries) {
            logger.info(mName + ": " + error + " -> Retrying");
            timesFailed++;
            this.currDownloadLink.setProperty(NICE_HOSTproperty + "failedtimes_" + error, timesFailed);
            throw new PluginException(LinkStatus.ERROR_RETRY, error);
        } else {
            this.currDownloadLink.setProperty(NICE_HOSTproperty + "failedtimes_" + error, Property.NULL);
            logger.info(mName + ": " + error + " -> Disabling current host");
            tempUnavailableHoster(timeout, error);
        }
    }

    /** no override to keep plugin compatible to old stable */
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        synchronized (hostUnavailableMap) {
            HashMap<String, UnavailableHost> unavailableMap = hostUnavailableMap.get(null);
            UnavailableHost nue = unavailableMap != null ? (UnavailableHost) unavailableMap.get(link.getHost()) : null;
            if (nue != null) {
                final Long lastUnavailable = nue.getErrorTimeout();
                final String errorReason = nue.getErrorReason();
                if (lastUnavailable != null && System.currentTimeMillis() < lastUnavailable) {
                    final long wait = lastUnavailable - System.currentTimeMillis();
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Host is temporarily unavailable for this multihoster: " + errorReason != null ? errorReason : "via " + this.getHost(), wait);
                } else if (lastUnavailable != null) {
                    unavailableMap.remove(link.getHost());
                    if (unavailableMap.size() == 0) {
                        hostUnavailableMap.remove(null);
                    }
                }
            }
            unavailableMap = hostUnavailableMap.get(account);
            nue = unavailableMap != null ? (UnavailableHost) unavailableMap.get(link.getHost()) : null;
            if (nue != null) {
                final Long lastUnavailable = nue.getErrorTimeout();
                final String errorReason = nue.getErrorReason();
                if (lastUnavailable != null && System.currentTimeMillis() < lastUnavailable) {
                    final long wait = lastUnavailable - System.currentTimeMillis();
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Host is temporarily unavailable for this account: " + errorReason != null ? errorReason : "via " + this.getHost(), wait);
                } else if (lastUnavailable != null) {
                    unavailableMap.remove(link.getHost());
                    if (unavailableMap.size() == 0) {
                        hostUnavailableMap.remove(account);
                    }
                }
            }
        }

        login(account, false);
        setConstants(account, link);
        showMessage(link, "Task 1: Generating Link");
        /* request Download */
        if (link.getStringProperty("pass", null) != null) {
            getPage(mServ + "/getFiles.aspx?ourl=" + Encoding.urlEncode(link.getDownloadURL()) + "&FilePass=" + Encoding.urlEncode(link.getStringProperty("pass", null)));
        } else {
            getPage(mServ + "/getFiles.aspx?ourl=" + Encoding.urlEncode(link.getDownloadURL()));
        }
        // handleErrors();
        br.setFollowRedirects(false);
        final String continuePage = br.getRedirectLocation();
        getPage(continuePage);
        String dllink = br.getRedirectLocation();
        if (dllink == null) {
            // not defect, zevera will respond with invalid redirect
            // throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            // ----------------Response Information------------
            // Connection-Time: keep-Alive
            // ----------------Response------------------------
            // HTTP/1.1 302 Found
            // Cache-Control: private
            // Content-Type: text/html; charset=utf-8
            // Server: Microsoft-IIS/8.0
            // X-AspNet-Version: 4.0.30319
            // X-Powered-By: ASP.NET
            // Date: Wed, 01 Jul 2015 21:55:08 GMT
            // Content-Length: 117
            // ------------------------------------------------
            //
            //
            // --ID:1927TS:1435787721587-7/2/15 5:55:21 AM - [jd.http.Browser(loadConnection)] ->
            // <html><head><title>Object moved</title></head><body>
            // <h2>Object moved to <a href="">here</a>.</h2>
            // </body></html>
        }
        handleRedirectionErrors(dllink);

        showMessage(link, "Task 2: Download begins!");
        handleDL(link, dllink);
    }

    private void handleRedirectionErrors(final String dllink) throws PluginException {
        if (dllink == null) {
            handleErrorRetries("dllink_null_redirect", 20, 1 * 60 * 60 * 1000l);
        } else if (new Regex(dllink, "/member/systemmessage\\.aspx\\?hoster=[\\w\\.\\-]+_customer").matches()) {
            // out of traffic for that given host for that given account.
            tempUnavailableHoster(1 * 60 * 60 * 1000l, "No traffic left for this host.");
        } else if (new Regex(dllink, "/member/systemmessage\\.aspx\\?hoster=[\\w\\.\\-]+$").matches()) {
            // out of traffic for that given host for whole of multihoster, set to null account to prevent retry across different accounts.
            this.currAcc = null;
            tempUnavailableHoster(30 * 60 * 1000l, "No traffic left for this host.");
        } else if (new Regex(dllink, "/member/systemmessage\\.aspx").matches()) {
            // we assume that they might have other error types for that same URL.
            handleErrorRetries("known_unknownerror", 20, 1 * 60 * 60 * 1000l);
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        setConstants(account, null);
        AccountInfo ai = new AccountInfo();
        try {
            login(account, true);
        } catch (PluginException e) {
            account.setValid(false);
            ai.setProperty("multiHostSupport", Property.NULL);
            throw e;
        }
        account.setValid(true);
        account.setConcurrentUsePossible(true);
        account.setMaxSimultanDownloads(-1);
        // doesn't contain useful info....
        // br.getPage(mServ + "/jDownloader.ashx?cmd=accountinfo");
        // grab website page instead.
        getPage(mServ + "/Member/Dashboard.aspx");
        final String expire = br.getRegex(">Expiration date:</label>.+?txExpirationDate\">(.*?)</label").getMatch(0);
        final String server = br.getRegex(">Server time:</label>.+?txServerTime\">(.*?)</label").getMatch(0);
        String expireTime = new Regex(expire, "(\\d+/\\d+/\\d+ [\\d\\:]+ (AM|PM))").getMatch(0);
        String serverTime = new Regex(server, "(\\d+/\\d+/\\d+ [\\d\\:]+ (AM|PM))").getMatch(0);
        long eTime = -1, sTime = -1;
        if (expireTime != null) {
            eTime = TimeFormatter.getMilliSeconds(expireTime, "MM/dd/yyyy hh:mm a", null);
            if (eTime == -1) {
                eTime = TimeFormatter.getMilliSeconds(expireTime, "MM/dd/yyyy hh:mm:ss a", null);
            }
        }
        if (serverTime != null) {
            sTime = TimeFormatter.getMilliSeconds(serverTime, "MM/dd/yyyy hh:mm a", null);
            if (sTime == -1) {
                sTime = TimeFormatter.getMilliSeconds(serverTime, "MM/dd/yyyy hh:mm:ss a", null);
            }
        }
        if (eTime >= 0 && sTime >= 0) {
            // accounts for different time zones! Should always be correct assuming the user doesn't change time every five minutes. Adjust
            // expire time based on users current system time.
            ai.setValidUntil(System.currentTimeMillis() + (eTime - sTime));
        } else if (eTime >= 0) {
            // fail over..
            ai.setValidUntil(eTime);
        } else {
            if (StringUtils.contains(expire, "NEVER")) {
                final String dayTraffic = br.getRegex(">Day Traffic left:</label>.+?txDayTrafficLeft\">(.*?)</label").getMatch(0);
                if (dayTraffic != null) {
                    final String dayTrafficLeft = new Regex(dayTraffic, "(\\d+ (MB|GB|TB))").getMatch(0);
                    if (dayTrafficLeft != null) {
                        ai.setTrafficLeft(SizeFormatter.getSize(dayTrafficLeft));
                    }
                }
            } else {
                // epic fail?
                logger.warning("Expire time could not be found/set. Please report to JDownloader Development Team");
            }
        }
        ai.setStatus("Premium User");
        try {
            getPage(mServ + "/jDownloader.ashx?cmd=gethosters");
            String[] hosts = br.getRegex("([^,]+)").getColumn(0);
            ArrayList<String> supportedHosts = new ArrayList<String>(Arrays.asList(hosts));
            /*
             * set ArrayList<String> with all supported multiHosts of this service
             */
            // we used local cached links provided by our decrypter, locked to ip addresses. Can not use multihoster
            ai.setMultiHostSupport(this, supportedHosts);
        } catch (Throwable e) {
            logger.info("Could not fetch ServerList from Multishare: " + e.toString());
        }
        return ai;
    }

    private void login(Account account, boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                /** Load cookies */
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
                            this.br.setCookie(mProt + mName, key, value);
                        }
                        return;
                    }
                }
                getPage(mServ + "/");
                getPage(mServ + "/OfferLogin.aspx?login=" + Encoding.urlEncode(account.getUser()) + "&pass=" + Encoding.urlEncode(account.getPass()));
                if (br.getCookie(mProt + mName, ".ASPNETAUTH") == null) {
                    // they can make more steps here.
                    final Form more = getMoreForm(account);
                    if (more != null) {
                        submitForm(more);
                    }
                    if (br.getCookie(mProt + mName, ".ASPNETAUTH") == null) {
                        final String lang = System.getProperty("user.language");
                        if ("de".equalsIgnoreCase(lang)) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                }
                /** Save cookies */
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = this.br.getCookies(mProt + mName);
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

    private Form getMoreForm(final Account account) {
        final Form more = br.getFormbyActionRegex("OfferLogin\\.aspx\\?login=" + Pattern.quote(Encoding.urlEncode(account.getUser())) + "&(?:amp;)?pass=" + Pattern.quote(Encoding.urlEncode(account.getPass())));
        return more;
    }

    private void tempUnavailableHoster(final long timeout, final String reason) throws PluginException {
        if (this.currDownloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unable to handle this errorcode!");
        }

        final UnavailableHost nue = new UnavailableHost(System.currentTimeMillis() + timeout, reason);

        synchronized (hostUnavailableMap) {
            HashMap<String, UnavailableHost> unavailableMap = hostUnavailableMap.get(this.currAcc);
            if (unavailableMap == null) {
                unavailableMap = new HashMap<String, UnavailableHost>();
                hostUnavailableMap.put(this.currAcc, unavailableMap);
            }
            unavailableMap.put(this.currDownloadLink.getHost(), nue);
        }
        throw new PluginException(LinkStatus.ERROR_RETRY);
    }

    private void showMessage(DownloadLink link, String message) {
        link.getLinkStatus().setStatusText(message);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}