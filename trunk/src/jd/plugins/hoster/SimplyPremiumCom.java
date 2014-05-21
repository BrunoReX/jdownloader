//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

import org.appwork.uio.UIOManager;
import org.appwork.utils.swing.dialog.ContainerDialog;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogNoAnswerException;
import org.jdownloader.DomainInfo;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "simply-premium.com" }, urls = { "REGEX_NOT_POSSIBLE_RANDOM-asdfasdfsadfsfs2133" }, flags = { 2 })
public class SimplyPremiumCom extends PluginForHost {

    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap = new HashMap<Account, HashMap<String, Long>>();
    private static final String                            NOCHUNKS           = "NOCHUNKS";

    private static final String                            NICE_HOST          = "simply-premium.com";
    private static final String                            NICE_HOSTproperty  = "simplypremiumcom";
    private static String                                  APIKEY             = null;
    private static Object                                  LOCK               = new Object();

    /* Default value is 3 */
    private static AtomicInteger                           maxPrem            = new AtomicInteger(3);

    public SimplyPremiumCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.simply-premium.com/vip.php");
    }

    @Override
    public String getAGBLink() {
        return "http://www.simply-premium.com/terms_and_conditions.php";
    }

    private Browser newBrowser() {
        br = new Browser();
        br.setCookiesExclusive(true);
        /* define custom browser headers and language settings. */
        br.getHeaders().put("Accept", "application/json");
        br.getHeaders().put("User-Agent", "JDownloader");
        br.setCustomCharset("utf-8");
        br.setConnectTimeout(60 * 1000);
        br.setReadTimeout(60 * 1000);
        return br;
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws PluginException {
        return AvailableStatus.UNCHECKABLE;
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) {
        if (account == null) {
            /* without account its not possible to download the link */
            return false;
        }
        synchronized (hostUnavailableMap) {
            HashMap<String, Long> unavailableMap = hostUnavailableMap.get(account);
            if (unavailableMap != null) {
                Long lastUnavailable = unavailableMap.get(downloadLink.getHost());
                if (lastUnavailable != null && System.currentTimeMillis() < lastUnavailable) {
                    return false;
                } else if (lastUnavailable != null) {
                    unavailableMap.remove(downloadLink.getHost());
                    if (unavailableMap.size() == 0) {
                        hostUnavailableMap.remove(account);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        /* handle premium should never be called */
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    private void handleDL(final Account account, final DownloadLink link, final String dllink) throws Exception {
        /* we want to follow redirects in final stage */
        br.setFollowRedirects(true);

        final boolean resume_allowed = account.getBooleanProperty("resume_allowed", false);

        int maxChunks = (int) account.getLongProperty("maxconnections", 1);
        if (maxChunks > 20) {
            maxChunks = 0;
        }
        if (link.getBooleanProperty(NOCHUNKS, false)) {
            maxChunks = 1;
        }
        if (!resume_allowed) {
            maxChunks = 1;
        }

        link.setProperty(NICE_HOSTproperty + "directlink", dllink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, resume_allowed, maxChunks);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            downloadErrorhandling(account, link);
            logger.info(NICE_HOST + ": Unknown download error");
            int timesFailed = link.getIntegerProperty(NICE_HOSTproperty + "timesfailed_unknowndlerror", 0);
            link.getLinkStatus().setRetryCount(0);
            if (timesFailed <= 2) {
                timesFailed++;
                link.setProperty(NICE_HOSTproperty + "timesfailed_unknowndlerror", timesFailed);
                throw new PluginException(LinkStatus.ERROR_RETRY, "Unknown error");
            } else {
                link.setProperty(NICE_HOSTproperty + "timesfailed_unknowndlerror", Property.NULL);
                logger.info(NICE_HOST + ": Unknown error - disabling current host!");
                tempUnavailableHoster(account, link, 60 * 60 * 1000l);
            }
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
                if (link.getBooleanProperty(SimplyPremiumCom.NOCHUNKS, false) == false) {
                    link.setProperty(SimplyPremiumCom.NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            }
        } catch (final PluginException e) {
            // New V2 errorhandling
            /* unknown error, we disable multiple chunks */
            if (e.getLinkStatus() != LinkStatus.ERROR_RETRY && link.getBooleanProperty(SimplyPremiumCom.NOCHUNKS, false) == false) {
                link.setProperty(SimplyPremiumCom.NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            throw e;
        }
    }

    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        this.br = newBrowser();
        getapikey(account);
        showMessage(link, "Task 1: Checking link");
        String dllink = checkDirectLink(link, NICE_HOSTproperty + "directlink");
        if (dllink == null) {
            /* request download information */
            br.setFollowRedirects(true);
            br.getPage("http://www.simply-premium.com/premium.php?info=&link=" + Encoding.urlEncode(link.getDownloadURL()));
            downloadErrorhandling(account, link);

            /* request download */
            dllink = getXML("download");
            if (dllink == null) {
                logger.info(NICE_HOST + ": dllinknull");
                int timesFailed = link.getIntegerProperty(NICE_HOSTproperty + "timesfailed_dllinknull", 0);
                link.getLinkStatus().setRetryCount(0);
                if (timesFailed <= 2) {
                    timesFailed++;
                    link.setProperty(NICE_HOSTproperty + "timesfailed_dllinknull", timesFailed);
                    throw new PluginException(LinkStatus.ERROR_RETRY, "dllinknull");
                } else {
                    link.setProperty(NICE_HOSTproperty + "timesfailed_dllinknull", Property.NULL);
                    logger.info(NICE_HOST + ": dllinknull - disabling current host!");
                    tempUnavailableHoster(account, link, 60 * 60 * 1000l);
                }
            }
        }
        showMessage(link, "Task 2: Download begins!");
        handleDL(account, link, dllink);
    }

    private void downloadErrorhandling(final Account account, final DownloadLink link) throws PluginException {
        if (br.containsHTML("<error>NOTFOUND</error>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.containsHTML("<error>hostererror</error>")) {
            logger.info(NICE_HOST + ": Host is unavailable at the moment -> Disabling it");
            tempUnavailableHoster(account, link, 60 * 60 * 1000l);
        } else if (br.containsHTML("<error>maxconnection</error>")) {
            logger.info(NICE_HOST + ": Too many simultan connections");
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait before starting new downloads", 5 * 60 * 1000l);
        } else if (br.containsHTML("<error>notvalid</error>")) {
            logger.info(NICE_HOST + ": Account invalid -> Disabling it");
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        } else if (br.containsHTML("<error>trafficlimit</error>")) {
            logger.info(NICE_HOST + ": Traffic limit reached -> Temp. disabling account");
            throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
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
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        this.br = newBrowser();
        final AccountInfo ai = new AccountInfo();
        br.setFollowRedirects(true);
        getapikey(account);
        br.getPage("http://simply-premium.com/api/user.php?apikey=" + APIKEY);
        account.setValid(true);
        account.setConcurrentUsePossible(true);
        final String acctype = getXML("account_typ");
        if (acctype == null) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        if (!acctype.matches("1|2") || !br.containsHTML("<vip>1</vip>")) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nNicht unterstützter Accounttyp!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUnsupported account type!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        String accdesc = null;
        if ("1".equals(acctype)) {
            ai.setUnlimitedTraffic();
            String expire = getXML("timeend");
            expire = expire.trim();
            if (expire.equals("")) {
                ai.setExpired(true);
                return ai;
            }
            final Long expirelng = Long.parseLong(expire);
            ai.setValidUntil(expirelng * 1000);
            accdesc = getPhrase("ACCOUNT_TYPE_TIME");
        } else {
            ai.setTrafficLeft(getXML("remain_traffic"));
            accdesc = getPhrase("ACCOUNT_TYPE_VOLUME");
        }

        int maxSimultanDls = Integer.parseInt(getXML("max_downloads"));
        if (maxSimultanDls < 1) {
            maxSimultanDls = 1;
        } else if (maxSimultanDls > 20) {
            maxSimultanDls = 20;
        }
        account.setMaxSimultanDownloads(maxSimultanDls);
        maxPrem.set(maxSimultanDls);

        long maxChunks = Integer.parseInt(getXML("chunks"));
        if (maxChunks > 1) {
            maxChunks = -maxChunks;
        }
        final boolean resumeAllowed = "1".equals(getXML("resume"));
        account.setProperty("maxconnections", maxChunks);
        account.setProperty("max_downloads", maxSimultanDls);
        account.setProperty("acc_type", accdesc);
        account.setProperty("resume_allowed", resumeAllowed);

        /* online=1 == show only working hosts */
        br.getPage("http://www.simply-premium.com/api/hosts.php?online=1");
        final ArrayList<String> supportedHosts = new ArrayList<String>();
        final String[] hostDomains = br.getRegex("<host>([^<>\"]*?)</host>").getColumn(0);
        for (final String domain : hostDomains) {
            supportedHosts.add(domain);
        }
        if (supportedHosts.contains("uploaded.net") || supportedHosts.contains("ul.to") || supportedHosts.contains("uploaded.to")) {
            if (!supportedHosts.contains("uploaded.net")) {
                supportedHosts.add("uploaded.net");
            }
            if (!supportedHosts.contains("ul.to")) {
                supportedHosts.add("ul.to");
            }
            if (!supportedHosts.contains("uploaded.to")) {
                supportedHosts.add("uploaded.to");
            }
        }
        ai.setStatus(accdesc + " - " + supportedHosts.size() + " hosts available");
        ai.setProperty("multiHostSupport", supportedHosts);
        return ai;
    }

    private void getapikey(final Account acc) throws IOException, Exception {
        synchronized (LOCK) {
            boolean acmatch = Encoding.urlEncode(acc.getUser()).equals(acc.getStringProperty("name", Encoding.urlEncode(acc.getUser())));
            if (acmatch) {
                acmatch = Encoding.urlEncode(acc.getPass()).equals(acc.getStringProperty("pass", Encoding.urlEncode(acc.getPass())));
            }
            APIKEY = acc.getStringProperty(NICE_HOSTproperty + "apikey", null);
            if (APIKEY != null && acmatch) {
                br.setCookie("http://simply-premium.com/", "apikey", APIKEY);
            } else {
                login(acc);
            }
        }
    }

    private void login(final Account account) throws IOException, Exception {
        final String lang = System.getProperty("user.language");
        br.getPage("http://simply-premium.com/login.php?login_name=" + Encoding.urlEncode(account.getUser()) + "&login_pass=" + Encoding.urlEncode(account.getPass()));
        if (br.containsHTML("<error>captcha_required</error>")) {
            final DownloadLink dummyLink = new DownloadLink(this, "Account", "simply-premium.com", "http://simply-premium.com", true);
            final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
            final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
            rc.setId("6LfBs-8SAAAAAKRWHd9j-sq-qpoOnjwoZlA4s3Ix");
            rc.load();
            for (int i = 1; i <= 3; i++) {
                final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                final String c = getCaptchaCode(cf, dummyLink);
                br.getPage("http://simply-premium.com/login.php?login_name=" + Encoding.urlEncode(account.getUser()) + "&login_pass=" + Encoding.urlEncode(account.getPass()) + "&recaptcha_challenge_field=" + rc.getChallenge() + "&recaptcha_response_field=" + Encoding.urlEncode(c));
                if (br.containsHTML("<error>captcha_required</error>")) {
                    rc.reload();
                    continue;
                }
                break;
            }
            if (br.containsHTML("<error>captcha_required</error>")) {
                if ("de".equalsIgnoreCase(lang)) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername, ungültiges Passwort und/oder ungültiges Login-Captcha!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password and/or invalid login-captcha!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
        }
        if (br.containsHTML("<error>not_valid_ip</error>")) {
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "Invalid IP / Ungültige IP", PluginException.VALUE_ID_PREMIUM_DISABLE);
        } else if (br.containsHTML("<error>no_traffic</error>")) {
            logger.info("Account has no traffic");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "Kein Traffic", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "No traffic", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }

        } else if (br.containsHTML("<error>no_longer_valid</error>")) {
            account.getAccountInfo().setExpired(true);
            account.setValid(false);
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nAccount abgelaufen!", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nAccount expired!", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
        }
        APIKEY = br.getRegex("<apikey>([A-Za-z0-9]+)</apikey>").getMatch(0);
        if (APIKEY == null || br.containsHTML("<error>not_valid</error>")) {
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        account.setProperty(NICE_HOSTproperty + "apikey", APIKEY);
        account.setProperty("name", Encoding.urlEncode(account.getUser()));
        account.setProperty("pass", Encoding.urlEncode(account.getPass()));
    }

    private void showMessage(DownloadLink link, String message) {
        link.getLinkStatus().setStatusText(message);
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
            /* wait 30 mins to retry this host */
            unavailableMap.put(downloadLink.getHost(), (System.currentTimeMillis() + timeout));
        }
        throw new PluginException(LinkStatus.ERROR_RETRY);
    }

    private String getXML(final String parameter) {
        return br.getRegex("<" + parameter + "( type=\"[^<>\"/]*?\")?>([^<>]*?)</" + parameter + ">").getMatch(1);
    }

    @Override
    public int getMaxSimultanDownload(final DownloadLink link, final Account account) {
        return maxPrem.get();
    }

    public void showAccountDetailsDialog(final Account account) {
        final AccountInfo ai = account.getAccountInfo();
        if (ai != null) {
            final String windowTitleLangText = "Account Zusatzinformationen";
            int maxChunks = (int) account.getLongProperty("maxconnections", 1);
            if (maxChunks < 0) {
                maxChunks = maxChunks * -1;
            }
            int max_dls = (int) account.getLongProperty("max_downloads", 1);
            final String accType = account.getStringProperty("acc_type", "?");
            final boolean resume_allowed = account.getBooleanProperty("resume_allowed", false);
            String resume_string;
            if (resume_allowed) {
                resume_string = getPhrase("DOWNLOAD_RESUMABLE_TRUE");
            } else {
                resume_string = getPhrase("DOWNLOAD_RESUMABLE_FALSE");
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
                logger.info("save.tv revision number error: " + e);
            }

            panelGenerator.addCategory("Account");
            panelGenerator.addEntry("Name:", account.getUser());
            panelGenerator.addEntry("Account Typ:", accType);

            panelGenerator.addCategory("Download");
            panelGenerator.addEntry("Max. Anzahl gleichzeitiger Downloads:", Integer.toString(max_dls));
            panelGenerator.addEntry("Max. Anzahl Verbindungen pro Datei (Chunks):", Integer.toString(maxChunks));
            panelGenerator.addEntry("Abgebrochene Downloads fortsetzbar:", resume_string);

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

    private HashMap<String, String> phrasesEN = new HashMap<String, String>() {
                                                  {
                                                      put("ACCOUNT_TYPE", "Account type:");
                                                      put("ACCOUNT_TYPE_TIME", "Time account");
                                                      put("ACCOUNT_TYPE_VOLUME", "Volume account");
                                                      put("DOWNLOAD_MAXSIMULTAN", "Max. number of simultan downloads:");
                                                      put("DOWNLOAD_MAXCHUNKS", "Max. chunks (connections per file):");
                                                      put("DOWNLOAD_RESUMABLE", "Resuming of stopped downloads possible:");
                                                      put("DOWNLOAD_RESUMABLE_TRUE", "Yes");
                                                      put("DOWNLOAD_RESUMABLE_FALSE", "No");
                                                  }
                                              };

    private HashMap<String, String> phrasesDE = new HashMap<String, String>() {
                                                  {
                                                      put("ACCOUNT_TYPE", "Account Typ:");
                                                      put("ACCOUNT_TYPE_TIME", "Zeitaccount");
                                                      put("ACCOUNT_TYPE_VOLUME", "Volumenaccount");
                                                      put("DOWNLOAD_MAXSIMULTAN", "Max. Anzahl gleichzeitiger Downloads:");
                                                      put("DOWNLOAD_MAXCHUNKS", "Max. Anzahl Verbindungen pro Datei (Chunks):");
                                                      put("DOWNLOAD_RESUMABLE", "Abgebrochene Downloads fortsetzbar:");
                                                      put("DOWNLOAD_RESUMABLE_TRUE", "Ja");
                                                      put("DOWNLOAD_RESUMABLE_FALSE", "Nein");
                                                  }
                                              };

    /**
     * Returns a germen/english translation of a phrase - we don't use the JDownloader translation framework since we need only germen and
     * english (provider is german)
     * 
     * @param key
     * @return
     */
    private String getPhrase(String key) {
        if ("de".equals(System.getProperty("user.language")) && phrasesDE.containsKey(key)) {
            return phrasesDE.get(key);
        } else if (phrasesEN.containsKey(key)) {
            return phrasesEN.get(key);
        }
        return "Translation not found!";
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}