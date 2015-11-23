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

import java.util.ArrayList;
import java.util.HashMap;

import jd.PluginWrapper;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "multidown.co.il" }, urls = { "REGEX_NOT_POSSIBLE_RANDOM-asdfasdfsadfsdgfd32423" }, flags = { 2 })
public class MultidownCoIl extends PluginForHost {

    private static HashMap<Account, HashMap<String, Long>> hostUnavailableMap = new HashMap<Account, HashMap<String, Long>>();

    public MultidownCoIl(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://multidown.co.il/");
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ac = new AccountInfo();
        br.setConnectTimeout(60 * 1000);
        br.setReadTimeout(60 * 1000);
        String page = null;
        String hosts = null;
        // check if account is valid
        page = br.getPage("http://multidown.co.il/api.php?user=" + Encoding.urlEncode(account.getUser()) + "&pass=" + account.getPass() + "&link={stupid_workaround_to_get_pw_ok}");
        if (this.br.getHttpConnection().getResponseCode() == 404) {
            if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername, Passwort oder login Captcha!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłędny użytkownik/hasło lub kod Captcha wymagany do zalogowania!\r\nUpewnij się, że prawidłowo wprowadziłes hasło i nazwę użytkownika. Dodatkowo:\r\n1. Jeśli twoje hasło zawiera znaki specjalne, zmień je (usuń) i spróbuj ponownie!\r\n2. Wprowadź hasło i nazwę użytkownika ręcznie bez użycia opcji Kopiuj i Wklej.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or login captcha!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        String error = "";
        try {
            error = getRegexTag(page, "error").getMatch(0);
        } catch (final Exception e) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "server error. Please try later.", 10 * 60 * 1000l);
        }
        if (!error.equalsIgnoreCase("Host not supported") && !error.equalsIgnoreCase("Host not supported or under maintenance") && !error.equalsIgnoreCase("\u05e9\u05e8\u05ea \u05dc\u05d0 \u05d6\u05de\u05d9\u05df")) {
            // wrong pass
            account.setValid(false);
            ac.setStatus("account invalid. Wrong password?");
            return ac;
        }
        // account is valid, check if expired:
        page = br.getPage("http://multidown.co.il/api.php?user=" + Encoding.urlEncode(account.getUser()) + "&pass=" + account.getPass());
        long daysLeft = -1;
        try {
            daysLeft = Long.parseLong(getRegexTag(page, "daysleft").getMatch(0));
        } catch (final Exception e) {
        }
        account.setValid(true);
        long validuntil = System.currentTimeMillis() + (daysLeft * 1000 * 60 * 60 * 24);
        ac.setValidUntil(validuntil);
        ArrayList<String> supportedHosts = new ArrayList<String>();
        hosts = br.getPage("http://multidown.co.il/api.php?hosts=1");
        if (hosts == null || hosts.isEmpty()) {
            account.setValid(false);
            ac.setStatus("cn not get supported hosters.");
            return ac;
        }
        final String hosters[] = new Regex(hosts, "'([^,]*)'").getColumn(0);
        for (String host : hosters) {
            supportedHosts.add(host.trim());
        }
        ac.setStatus("Account valid");
        ac.setMultiHostSupport(this, supportedHosts);
        return ac;
    }

    @Override
    public String getAGBLink() {
        return "http://multidown.co.il/terms-of-service/";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    private void showMessage(DownloadLink link, String message) {
        link.getLinkStatus().setStatusText(message);
    }

    /** no override to keep plugin compatible to old stable */
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {

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

        showMessage(link, "Phase 1/2: Generating Link");
        String page = br.getPage("http://multidown.co.il/api.php?user=" + Encoding.urlEncode(account.getUser()) + "&pass=" + Encoding.urlEncode(account.getPass()) + "&link=" + Encoding.urlEncode(link.getDownloadURL()));
        String error = "";
        try {
            error = getRegexTag(page, "error").getMatch(0);
        } catch (Exception e) {
            // we handle it 2 lines later
        }
        if (!(error == null || error.isEmpty())) {
            // better error handling possible if we got more information multihoster
            showMessage(link, "Error: " + error);
            tempUnavailableHoster(account, link, 20 * 60 * 1000l);
        }
        // hopefully no error, page should contain downloadlink
        String genlink = "";
        try {
            genlink = getRegexTag(page, "url").getMatch(0);
        } catch (Exception e) {
            // we handle it later
        }
        genlink = genlink.replaceAll("\\\\/", "/");
        if (!(genlink.startsWith("http://") || genlink.startsWith("https://"))) {
            logger.severe("Multidown.co.il(Error): " + genlink);
            /*
             * after x retries we disable this host and retry with normal plugin
             */
            if (link.getLinkStatus().getRetryCount() >= 3) {
                try {
                    // disable hoster for 30min
                    tempUnavailableHoster(account, link, 30 * 60 * 1000l);
                } catch (Exception e) {
                }
                /* reset retrycounter */
                link.getLinkStatus().setRetryCount(0);
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE);
            }
            String msg = "(" + link.getLinkStatus().getRetryCount() + 1 + "/" + 3 + ")";
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Retry in few secs" + msg, 20 * 1000l);
        }
        br.setFollowRedirects(true);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, genlink, true, 0);
        if (dl.getConnection().getResponseCode() == 404) {
            /* file offline */
            dl.getConnection().disconnect();
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (!dl.getConnection().isContentDisposition()) {
            /* unknown error */
            br.followConnection();
            logger.severe("Multidown.co.il(Error): " + br.toString());
            // disable hoster for 5min
            tempUnavailableHoster(account, link, 5 * 60 * 1000l);
            // throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE);
        }
        showMessage(link, "Phase 2/2: Download begins!");
        dl.startDownload();
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws Exception {
        return AvailableStatus.UNCHECKABLE;
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
    }

    private Regex getRegexTag(final String someText, final String tag) {
        // example: "error":"Host not supported"
        return new Regex(someText, "\"" + tag + "\":\"([^\"]*)\"");
    }

}