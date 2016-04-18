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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.JComponent;
import javax.swing.JLabel;

import jd.PluginWrapper;
import jd.config.Property;
import jd.gui.swing.components.linkbutton.JLink;
import jd.http.Browser;
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

import org.appwork.swing.MigPanel;
import org.appwork.swing.components.ExtPasswordField;
import org.appwork.swing.components.ExtTextField;
import org.appwork.utils.StringUtils;
import org.jdownloader.gui.InputChangedCallbackInterface;
import org.jdownloader.plugins.accounts.AccountBuilderInterface;

@HostPlugin(revision = "$Revision: 26092 $", interfaceVersion = 3, names = { "freeway.bz" }, urls = { "REGEX_NOT_POSSIBLE_RANDOM-asdfasdfsadfsdgfd32424" }, flags = { 2 })
public class FreewayBz extends antiDDoSForHost {

    // DEV NOTES
    // password is APIKey from users profile.
    // using same API: freeway.bz, myfastfile.com
    // API description: https://www.myfastfile.com/filehostapi

    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap = new HashMap<Account, HashMap<String, Long>>();
    private final String                                   mName              = "www.freeway.bz";
    private static final String                            NICE_HOST          = "freeway.bz";
    private static final String                            NICE_HOSTproperty  = NICE_HOST.replaceAll("(\\.|\\-)", "");
    private final String                                   mProt              = "https://";

    private int                                            statuscode         = 0;
    private Account                                        currAcc            = null;
    private DownloadLink                                   currDownloadLink   = null;

    // repeat is one more than desired
    private final int                                      globalRepeat       = 4;
    private final String                                   sessionRetry       = "sessionRetry";
    private final String                                   globalRetry        = "globalRetry";
    private static final long                              maxtraffic_daily   = 53687091200l;

    public FreewayBz(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(mProt + mName + "/premium");
    }

    @Override
    public String getAGBLink() {
        return mProt + mName + "/legal#tos";
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    @Override
    public AccountBuilderInterface getAccountFactory(InputChangedCallbackInterface callback) {
        return new FreewayBZAccountFactory(callback);
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        return AvailableStatus.UNCHECKABLE;
    }

    private void setConstants(final Account acc, final DownloadLink dl) {
        this.currAcc = acc;
        this.currDownloadLink = dl;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        setConstants(account, null);
        final AccountInfo ac = new AccountInfo();
        br.setFollowRedirects(true);
        getPage(mProt + mName + "/filehostapi?action=accountstatus&user_id=" + Encoding.urlEncode(account.getUser()) + "&pin=" + Encoding.urlEncode(account.getPass()));
        if ("error".equals(this.getJson("status"))) {
            if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername/Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        /* Account is valid, let's fetch account details */
        final String premium_until = getJson("premium_until");
        long premium_until_long = 0;
        if (premium_until.matches("\\d+")) {
            premium_until_long = Long.parseLong(premium_until);
        }
        try {
            if (premium_until_long > 0) {
                premium_until_long *= 1000l;
                ac.setValidUntil(premium_until_long);
                ac.setUnlimitedTraffic();
                account.setType(AccountType.PREMIUM);
                ac.setStatus("Premium Account");
            } else {
                ac.setTrafficLeft(0);
                /* TODO: Get this information via API and also show it for premium accounts */
                ac.setTrafficMax(maxtraffic_daily);
                account.setType(AccountType.FREE);
                ac.setStatus("Registered (free) account");
            }
        } catch (Exception e) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nCan not parse premium_until!", PluginException.VALUE_ID_PREMIUM_DISABLE);
        }

        // now it's time to get all supported hosts
        getPage("/filehostapi?action=hosts&user_id=" + Encoding.urlEncode(account.getUser()) + "&pin=" + Encoding.urlEncode(account.getPass()));
        if (inValidStatus()) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nCan not parse supported hosts!", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
        }
        final String hostsArray = getJsonArray("hosts");
        final String[] hosts = new Regex(hostsArray, "\"(.*?)\"").getColumn(0);
        ArrayList<String> supportedHosts = new ArrayList<String>(Arrays.asList(hosts));
        ac.setMultiHostSupport(this, supportedHosts);
        account.setValid(true);
        return ac;
    }

    /** no override to keep plugin compatible to old stable */
    /* TODO: Move all-or most of the errorhandling into handleAPIErrors */
    @SuppressWarnings("deprecation")
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        setConstants(account, link);

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

        // work around
        if (link.getBooleanProperty("hasFailed", false)) {
            final int hasFailedInt = link.getIntegerProperty("hasFailedWait", 60);
            // nullify old storeables
            link.setProperty("hasFailed", Property.NULL);
            link.setProperty("hasFailedWait", Property.NULL);
            sleep(hasFailedInt * 1001, link);
        }

        /* generate downloadlink */
        br.setFollowRedirects(true);
        getPage(mProt + mName + "/filehostapi?action=download&user_id=" + Encoding.urlEncode(account.getUser()) + "&pin=" + Encoding.urlEncode(account.getPass()) + "&link=" + Encoding.urlEncode(link.getDownloadURL()));

        // parse json
        if (br.containsHTML("Max atteint\\s*!")) {
            /* max retries for this host reached, try again in 1h */
            tempUnavailableHoster(account, link, 60 * 60 * 1000l);
        }

        if (br.containsHTML("nvalidlink")) {
            throw new PluginException(LinkStatus.ERROR_FATAL, "Invalid link/unsupported format");
        }

        String dllink = getJson("link");
        if (inValidate(dllink) || !dllink.matches("https?://.+|/.+")) {
            if (!inValidate(dllink) && dllink.contains("Cannot debrid link")) {
                logger.severe("Can not debrid link --> Temporarily disabling current host");
                tempUnavailableHoster(account, link, 20 * 60 * 1000l);
            }
            /* temp unavailable will ditch to next download candidate, and retry doesn't respect wait times... ! */
            link.setProperty("hasFailed", true);
            link.setProperty("hasFailedWait", 15);
            handleErrorRetries("unknown_dllinknull", 10);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, -12);
        if (dl.getConnection().getResponseCode() == 404) {
            /* file offline */
            try {
                dl.getConnection().disconnect();
            } catch (final Throwable e) {
            }
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (dl.getConnection().getResponseCode() == 500) {
            /* file offline */
            try {
                dl.getConnection().disconnect();
            } catch (final Throwable e) {
            }
            handleErrorRetries("unknown_servererror_500", 10);
        }
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            /* temp unavailable will ditch to next download candidate, and retry doesn't respect wait times... ! */
            link.setProperty("hasFailed", true);
            link.setProperty("hasFailedWait", 60);
            handleErrorRetries("unknowndlerror_html", 10);
        }
        dl.startDownload();
    }

    @Override
    protected void getPage(final String page) throws Exception {
        super.getPage(page);
        updatestatuscode();
        handleAPIErrors(br);
    }

    /** 0 = everything ok, 1-99 = possible errors */
    private void updatestatuscode() {
        String error = null;
        if (inValidStatus()) {
            error = getJson("msg");
        }
        if (error != null) {
            if (error.equals("Cannot login Check your username or pass")) {
                statuscode = 1;
            } else if (error.equals("Your account is not premium")) {
                statuscode = 2;
            } else {
                /* TODO: Enable code below once all known errors are correctly handled */
                // statuscode = 666;
            }
        } else {
            if (inValidStatus() && "null".equalsIgnoreCase(getJson("link"))) {
                statuscode = 3;
            } else {
                /* Everything should be ok */
                statuscode = 0;
            }
        }
    }

    private void handleAPIErrors(final Browser br) throws PluginException {
        String statusMessage = null;
        try {
            switch (statuscode) {
            case 0:
                /* Everything ok */
                break;
            case 1:
                /* Invalid account */
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    statusMessage = "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.\r\nWichtig: Das Passwort muss dein APIKey sein, siehe dein Profil auf der " + mName + " Webseite.";
                } else {
                    statusMessage = "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.\r\nNote: Password has to be APIKey, see Account Profile on " + mName + "website.";
                }
                throw new PluginException(LinkStatus.ERROR_PREMIUM, statusMessage, PluginException.VALUE_ID_PREMIUM_DISABLE);
            case 2:
                /*
                 * Free accounts have no traffic - disable them on downloadtry (should actually never happen as they're added with ZERO
                 * trafficleft)
                 */
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nNicht unterstützter Accounttyp!\r\nFalls du denkst diese Meldung sei falsch die Unterstützung dieses Account-Typs sich\r\ndeiner Meinung nach aus irgendeinem Grund lohnt,\r\nkontaktiere uns über das support Forum.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUnsupported account type!\r\nIf you think this message is incorrect or it makes sense to add support for this account type\r\ncontact us via our support forum.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            case 3:
                /* Server returns null-dllink --> Retry */
                handleErrorRetries("serverside_error_dllink_null", 10);
            default:
                /* Unknown error */
                logger.warning("Unknown API error happened!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unknown API error happened!");
                /* TODO: Implement all- or as many errors as possible, then activate the code below */
                // statusMessage = "Unknown error";
                // logger.info(NICE_HOST + ": Unknown API error");
                // handleErrorRetries("unknownAPIerror", 10);
            }
        } catch (final PluginException e) {
            logger.info(NICE_HOST + ": Exception: statusCode: " + statuscode + " statusMessage: " + statusMessage);
            throw e;
        }
    }

    private void tempUnavailableHoster(final long timeout) throws PluginException {
        if (this.currDownloadLink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unable to handle this errorcode!");
        }
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(this.currAcc);
            if (unavailableMap == null) {
                unavailableMap = new HashMap<String, Long>();
                hostUnavailableMap.put(this.currAcc, unavailableMap);
            }
            /* wait 30 mins to retry this host */
            unavailableMap.put(this.currDownloadLink.getHost(), (System.currentTimeMillis() + timeout));
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
            tempUnavailableHoster(1 * 60 * 60 * 1000l);
        }
    }

    private boolean inValidStatus() {
        return !"ok".equalsIgnoreCase(getJson("status"));
    }

    private void tempUnavailableHoster(Account account, DownloadLink downloadLink, long timeout) throws PluginException {
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
        // we reset session retry and int global here to allow for multiple retries, with tempUnailable inbetween
        downloadLink.setProperty(sessionRetry, Property.NULL);
        final int gr = downloadLink.getIntegerProperty(globalRetry, 0) + 1;
        downloadLink.setProperty(globalRetry, gr);
        if (gr >= globalRepeat) {
            // prevent more than globalRepeat retries.
            throw new PluginException(LinkStatus.ERROR_FATAL, "Exausted Global Retry!");
        }
        throw new PluginException(LinkStatus.ERROR_RETRY);
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) {
        return true;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
        link.setProperty(sessionRetry, Property.NULL);
        link.setProperty(globalRetry, Property.NULL);
    }

    public static class FreewayBZAccountFactory extends MigPanel implements AccountBuilderInterface {
        /**
         *
         */
        private static final long serialVersionUID = 1L;

        private final String      IDHELP           = "Enter your user id (9 digits)";
        private final String      PINHELP          = "Enter your pin";

        public boolean updateAccount(Account input, Account output) {
            boolean changed = false;
            if (!StringUtils.equals(input.getUser(), output.getUser())) {
                output.setUser(input.getUser());
                changed = true;
            }
            if (!StringUtils.equals(input.getPass(), output.getPass())) {
                output.setPass(input.getPass());
                changed = true;
            }
            return changed;
        }

        private String getPassword() {
            if (this.pass == null) {
                return null;
            }
            if (EMPTYPW.equals(new String(this.pass.getPassword()))) {
                return null;
            }
            return new String(this.pass.getPassword());
        }

        private String getUsername() {
            if (IDHELP.equals(this.name.getText())) {
                return null;
            }
            return this.name.getText();
        }

        private final ExtTextField     name;
        private final ExtPasswordField pass;

        private static String          EMPTYPW = "                 ";
        private final JLabel           idLabel;

        public FreewayBZAccountFactory(final InputChangedCallbackInterface callback) {
            super("ins 0, wrap 2", "[][grow,fill]", "");
            final String lang = System.getProperty("user.language");
            String usertext_finddata;
            String usertext_uid;
            if ("de".equalsIgnoreCase(lang)) {
                usertext_finddata = "Klicke hier um deine User-ID und PIN zu sehen:";
                usertext_uid = "User-ID (muss 9-stellig sein)";
            } else {
                usertext_finddata = "Click here to find your User-ID/PIN:";
                usertext_uid = "User-ID: (must be 9 digis)";
            }
            add(new JLabel(usertext_finddata));
            add(new JLink("https://www.freeway.bz/account"));
            add(idLabel = new JLabel(usertext_uid));
            add(this.name = new ExtTextField() {

                @Override
                public void onChanged() {
                    callback.onChangedInput(this);
                }

            });

            name.setHelpText(IDHELP);

            add(new JLabel("PIN:"));
            add(this.pass = new ExtPasswordField() {

                @Override
                public void onChanged() {
                    callback.onChangedInput(this);
                }

            }, "");
            pass.setHelpText(PINHELP);
        }

        @Override
        public JComponent getComponent() {
            return this;
        }

        @Override
        public void setAccount(Account defaultAccount) {
            if (defaultAccount != null) {
                name.setText(defaultAccount.getUser());
                pass.setText(defaultAccount.getPass());
            }
        }

        @Override
        public boolean validateInputs() {
            final String userName = getUsername();
            if (userName == null || !userName.trim().matches("^\\d{9}$")) {
                idLabel.setForeground(Color.RED);
                return false;
            }
            idLabel.setForeground(Color.BLACK);
            return getPassword() != null;
        }

        @Override
        public Account getAccount() {
            return new Account(getUsername(), getPassword());
        }
    }

}