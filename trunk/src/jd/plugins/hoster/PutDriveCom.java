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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
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

import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

@HostPlugin(revision = "$Revision: 26673$", interfaceVersion = 3, names = { "putdrive.com" }, urls = { "REGEX_NOT_POSSIBLE_RANDOM-asdfasdfsadfsdgfd32423" }, flags = { 2 })
public class PutDriveCom extends PluginForHost {

    /* Important - all of these belong together: zevera.com, multihosters.com, putdrive.com(?!) */
    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap = new HashMap<Account, HashMap<String, Long>>();
    private static final String                            NOCHUNKS           = "NOCHUNKS";

    private static final String                            NICE_HOST          = "putdrive.com";
    private static final String                            API_HTTP           = "http://";
    private static final String                            NICE_HOSTproperty  = NICE_HOST.replaceAll("(\\.|\\-)", "");

    private int                                            STATUSCODE         = 0;
    private Account                                        currAcc            = null;
    private DownloadLink                                   currDownloadLink   = null;

    public PutDriveCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.putdrive.com/Prices.aspx");
    }

    @Override
    public String getAGBLink() {
        return "http://www.putdrive.com/Terms.aspx";
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    private Browser newBrowser() {
        br = new Browser();
        br.setCookiesExclusive(true);
        // define custom browser headers and language settings.
        br.getHeaders().put("Accept", "application/json");
        br.getHeaders().put("User-Agent", "JDownloader");
        br.setCustomCharset("utf-8");
        br.setConnectTimeout(3 * 60 * 1000);
        br.setReadTimeout(3 * 60 * 1000);
        return br;
    }

    private void setConstants(final Account acc, final DownloadLink dl) {
        this.currAcc = acc;
        this.currDownloadLink = dl;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        setConstants(account, null);
        String hosts[] = null;
        this.br = newBrowser();
        final AccountInfo ac = new AccountInfo();
        api_loginJson(account);
        // login(account);
        final String trafficleft = PluginJSonUtils.getJson(br, "ExtraTrafficLeftInMBytes");
        ac.setTrafficLeft(Long.parseLong(trafficleft) * 1024 * 1024);
        final String expiredate = PluginJSonUtils.getJson(br, "ExpirationDate");
        ac.setValidUntil(TimeFormatter.getMilliSeconds(expiredate, "MM/dd/yyyy hh:mm:ss", Locale.ENGLISH));
        // now let's get a list of all supported hosts:
        br.getPage("http://putdrive.com/jdownloader.ashx?cmd=gethosters");
        hosts = br.toString().toLowerCase().split(",");
        final ArrayList<String> supportedHosts = new ArrayList<String>();
        for (String host : hosts) {
            if (!host.isEmpty()) {
                supportedHosts.add(host.trim());
            }
        }
        account.setType(AccountType.PREMIUM);
        account.setMaxSimultanDownloads(-1);
        account.setConcurrentUsePossible(true);
        ac.setMultiHostSupport(this, supportedHosts);
        ac.setStatus("Premium Account");
        return ac;
    }

    private void api_loginJson(final Account acc) throws IOException, PluginException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>");
        sb.append("<LoginUser_JSON xmlns=\"http://tempuri.org/\">");
        sb.append("<username>" + acc.getUser() + "</username>");
        sb.append("<Password>" + acc.getPass() + "</Password>");
        sb.append("</LoginUser_JSON>");
        sb.append("</soap12:Body></soap12:Envelope>");
        br.getHeaders().put("Content-Type", "application/soap+xml; charset=utf-8");
        boolean failed = false;
        try {
            br.postPageRaw("http://api.putdrive.com/DownloadAPI.asmx", sb.toString());
        } catch (final BrowserException e) {
            if (br.getRequest().getHttpConnection().getResponseCode() == 500) {
                failed = true;
            }
        }
        if (br.containsHTML("\"CustomerID\":\"\\-1\"")) {
            failed = true;
        }
        if (failed) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
    }

    private void api_chargetraffic(final Account acc, final DownloadLink dl) throws IOException, PluginException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>");
        sb.append("<ChargeTraffic xmlns=\"http://tempuri.org/\">");
        sb.append("<szLogin>" + acc.getUser() + "</szLogin>");
        sb.append("<szPass>" + acc.getPass() + "</szPass>");
        sb.append("<BytesToBeCharged>" + dl.getDownloadSize() + "</BytesToBeCharged>");
        sb.append("<LinkText>" + dl.getDownloadURL() + "</LinkText>");
        sb.append("<fileaName>" + dl.getName() + "</fileaName>");
        sb.append("</ChargeTraffic>");
        sb.append("</soap12:Body></soap12:Envelope>");
        br.getHeaders().put("Content-Type", "application/soap+xml; charset=utf-8");
        boolean failed = false;
        try {
            br.postPageRaw("http://api.putdrive.com/DownloadAPI.asmx", sb.toString());
        } catch (final BrowserException e) {
            if (br.getRequest().getHttpConnection().getResponseCode() == 500) {
                failed = true;
            }
        }
        if (br.containsHTML("\"CustomerID\":\"\\-1\"")) {
            failed = true;
        }
        if (failed) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    /** no override to keep plugin compatible to old stable */
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        setConstants(account, link);
        this.br = newBrowser();

        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap != null) {
                Long lastUnavailable = unavailableMap.get(link.getHost());
                if (lastUnavailable != null && System.currentTimeMillis() < lastUnavailable) {
                    final long wait = lastUnavailable - System.currentTimeMillis();
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Host is temporarily unavailable via " + this.getHost(), wait);
                } else if (lastUnavailable != null) {
                    unavailableMap.remove(link.getHost());
                    if (unavailableMap.size() == 0) {
                        hostUnavailableMap.remove(account);
                    }
                }
            }
        }

        br.setFollowRedirects(false);
        String dllink = checkDirectLink(link, NICE_HOSTproperty + "finallink");
        if (dllink == null) {
            br.getPage("http://putdrive.com/jdownloader.ashx?login=" + Encoding.urlEncode(account.getUser()) + "&pass=" + Encoding.urlEncode(account.getPass()) + "&cmd=generatedownloaddirect&olink=" + Encoding.urlEncode(link.getDownloadURL()));
            if (br.containsHTML("No trafic")) {
                logger.info(NICE_HOST + ": no traffic available");
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
            dllink = br.getRedirectLocation();
            if (dllink == null) {
                if (br.getRequest().getHttpConnection().getResponseCode() == 302) {
                    logger.info(NICE_HOST + ": 302 but no downloadlink");
                    handleErrorRetries("dllinknull_302", 10);
                } else if (br.containsHTML("No htmlCode read")) {
                    handleErrorRetries("dllinknull_no_htmlcode", 10);
                }
                handleErrorRetries("dllinknull", 10);
            }
            dllink = dllink.replace("\\", "");
        }
        int maxChunks = 0;
        if (link.getBooleanProperty(PutDriveCom.NOCHUNKS, false)) {
            maxChunks = 1;
        }

        try {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, maxChunks);
        } catch (final PluginException e) {
            /* Bad servers */
            if (e.getMessage() != null && e.getMessage().equals("Redirectloop")) {
                handleErrorRetries("dlstart_fail_redirectloop", 20);
            }
            e.printStackTrace();
            System.out.println("WTF");
            throw e;
        }
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            handleErrorRetries("unknowndlerror", 10);
        }
        link.setProperty(NICE_HOSTproperty + "finallink", dllink);
        boolean charge_ok = true;
        try {
            api_chargetraffic(account, link);
        } catch (final Throwable charge_error) {
            charge_ok = false;
        }
        if (charge_ok && !br.containsHTML("<ErrorCode>None</ErrorCode>")) {
            charge_ok = false;
        }
        if (charge_ok) {
            logger.info(NICE_HOST + ": Traffic charged successfully");
        } else {
            logger.warning(NICE_HOST + ": There was a problem with the traffic charge though the download worked fine");
        }
        try {
            if (!this.dl.startDownload()) {
                try {
                    if (dl.externalDownloadStop()) {
                        return;
                    }
                } catch (final Throwable e) {
                }
                /* unknown error, we disable multiple chunks */
                if (link.getBooleanProperty(PutDriveCom.NOCHUNKS, false) == false) {
                    link.setProperty(PutDriveCom.NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            }
        } catch (final PluginException e) {
            // New V2 errorhandling
            /* unknown error, we disable multiple chunks */
            if (e.getLinkStatus() != LinkStatus.ERROR_RETRY && link.getBooleanProperty(PutDriveCom.NOCHUNKS, false) == false) {
                link.setProperty(PutDriveCom.NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            throw e;
        }
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

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        return AvailableStatus.UNCHECKABLE;
    }

    private void tempUnavailableHoster(final Account account, final DownloadLink downloadLink, final long timeout) throws PluginException {
        if (downloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unable to handle this errorcode!");
        }
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap == null) {
                unavailableMap = new HashMap<String, Long>();
                hostUnavailableMap.put(account, unavailableMap);
            }
            /* wait to retry this host */
            unavailableMap.put(downloadLink.getHost(), (System.currentTimeMillis() + timeout));
        }
        throw new PluginException(LinkStatus.ERROR_RETRY);
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
    private void handleErrorRetries(final String error, final int maxRetries) throws PluginException {
        int timesFailed = this.currDownloadLink.getIntegerProperty(NICE_HOSTproperty + "failedtimes_" + error, 0);
        this.currDownloadLink.getLinkStatus().setRetryCount(0);
        if (timesFailed <= maxRetries) {
            logger.info(NICE_HOST + ": " + error + " -> Retrying");
            timesFailed++;
            this.currDownloadLink.setProperty(NICE_HOSTproperty + "failedtimes_" + error, timesFailed);
            throw new PluginException(LinkStatus.ERROR_RETRY, error);
        } else {
            this.currDownloadLink.setProperty(NICE_HOSTproperty + "failedtimes_" + error, Property.NULL);
            logger.info(NICE_HOST + ": " + error + " -> Disabling current host");
            tempUnavailableHoster(this.currAcc, this.currDownloadLink, 1 * 60 * 60 * 1000l);
            /* TODO: Remove plugin defect once all known errors are correctly handled */
            // throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, error);
        }
    }

    /** Unused API functions */
    // private void api_get_account_details(final Account acc) throws IOException, PluginException {
    // StringBuilder sb = new StringBuilder();
    // sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    // xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>");
    // sb.append("<getUserData xmlns=\"http://tempuri.org/\">");
    // sb.append("<szLogin>" + acc.getUser() + "</szLogin>");
    // sb.append("<szPass>" + acc.getPass() + "</szPass>");
    // sb.append("</getUserData>");
    // sb.append("</soap12:Body></soap12:Envelope>");
    // br.getHeaders().put("Content-Type", "application/soap+xml; charset=utf-8");
    // try {
    // br.postPage("http://api.putdrive.com/DownloadAPI.asmx", sb.toString());
    // } catch (final BrowserException e) {
    // if (br.getRequest().getHttpConnection().getResponseCode() == 500) {
    // final String lang = System.getProperty("user.language");
    // if ("de".equalsIgnoreCase(lang)) {
    // throw new PluginException(LinkStatus.ERROR_PREMIUM,
    // "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername
    // und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!",
    // PluginException.VALUE_ID_PREMIUM_DISABLE);
    // } else {
    // throw new PluginException(LinkStatus.ERROR_PREMIUM,
    // "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your
    // password contains special characters, change it (remove them) and try again!",
    // PluginException.VALUE_ID_PREMIUM_DISABLE);
    // }
    // }
    // }
    // }
    //
    // private void api_gethosterswithlimit() throws IOException, PluginException {
    // StringBuilder sb = new StringBuilder();
    // sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
    // xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>");
    // sb.append("<GetHostersWithLimit_JSON xmlns=\"http://tempuri.org/\" />");
    // sb.append("</soap12:Body></soap12:Envelope>");
    // br.getHeaders().put("Content-Type", "application/soap+xml; charset=utf-8");
    // br.postPage("http://api.putdrive.com/DownloadAPI.asmx", sb.toString());
    // }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) {
        return true;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}