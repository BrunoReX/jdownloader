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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.Cookie;
import jd.http.Cookies;
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
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.TimeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "vessel.com" }, urls = { "http://vessel\\.comdecrypted\\d+" }, flags = { 2 })
public class VesselCom extends PluginForHost {

    /** Settings stuff */
    private static final String                   FAST_LINKCHECK               = "FAST_LINKCHECK";

    /* Connection stuff */
    private static final boolean                  FREE_RESUME                  = true;
    private static final int                      FREE_MAXCHUNKS               = 0;
    private static final int                      FREE_MAXDOWNLOADS            = 20;
    // private static final boolean ACCOUNT_FREE_RESUME = true;
    // private static final int ACCOUNT_FREE_MAXCHUNKS = 0;
    // private static final int ACCOUNT_FREE_MAXDOWNLOADS = 20;
    // private static final boolean ACCOUNT_PREMIUM_RESUME = true;
    // private static final int ACCOUNT_PREMIUM_MAXCHUNKS = 0;
    private static final int                      ACCOUNT_PREMIUM_MAXDOWNLOADS = 20;

    /* don't touch the following! */
    private static AtomicInteger                  maxPrem                      = new AtomicInteger(1);

    /*
     * Available via HLS only: 144-64k,720-1500k, 720-3000k --> Basically lower qualities and some in between the http qualities so we're
     * not really missing anything by not downloading them.
     */
    public static LinkedHashMap<String, String[]> formats                      = new LinkedHashMap<String, String[]>() {
        {
            /*
             * Format-name:videoCodec, videoBitrate,
             * videoResolution, audioCodec, audioBitrate
             */
            put("mp4-216-250K", new String[] { "AVC", "250", "384x216", "AAC LC-SBR", "32" });
            put("mp4-360-500K", new String[] { "AVC", "500", "640x360", "AAC LC", "128" });
            put("mp4-480-1000K", new String[] { "AVC", "1000", "768x432", "AAC LC", "128" });
            put("mp4-720-2400K", new String[] { "AVC", "2400", "1280x720", "AAC LC", "160" });
            put("mp4-1080-4800K", new String[] { "AVC", "4800", "1920x1080", "AAC LC", "160" });

        }
    };

    private String                                DLLINK                       = null;

    @SuppressWarnings("deprecation")
    public VesselCom(final PluginWrapper wrapper) {
        super(wrapper);
        setConfigElements();
        this.enablePremium("https://www.vessel.com/");
    }

    @Override
    public String getAGBLink() {
        return "https://www.vessel.com/about/terms";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return FREE_MAXDOWNLOADS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        setBrowserExclusive();
        // final String mainlink = link.getStringProperty("mainlink", null);
        // br.getPage(mainlink);
        // if (br.getHttpConnection().getResponseCode() == 404) {
        // throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        // }
        final Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa == null) {
            link.getLinkStatus().setStatusText("Account needed to check links");
            return AvailableStatus.UNCHECKABLE;
        }
        login(this.br, aa, false);
        DLLINK = link.getStringProperty("directlink", null);
        URLConnectionAdapter con = null;
        try {
            try {
                if (isJDStable()) {
                    /* @since JD2 */
                    con = br.openHeadConnection(DLLINK);
                } else {
                    /* Not supported in old 0.9.581 Stable */
                    con = br.openGetConnection(DLLINK);
                }
            } catch (final BrowserException e) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (!con.getContentType().contains("html")) {
                link.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
    }

    private boolean isJDStable() {
        return System.getProperty("jd.revision.jdownloaderrevision") == null;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        doFree(link);
    }

    private void doFree(final DownloadLink link) throws Exception {
        br.setFollowRedirects(true);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, DLLINK, FREE_RESUME, FREE_MAXCHUNKS);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            }
            br.followConnection();
            try {
                dl.getConnection().disconnect();
            } catch (final Throwable e) {
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private static final String MAINPAGE = "http://vessel.com";
    private static Object       LOCK     = new Object();

    @SuppressWarnings("unchecked")
    public static void login(final Browser br, final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            br.setCookie(MAINPAGE, key, value);
                        }
                        return;
                    }
                }
                br.setFollowRedirects(false);
                br.setAllowedResponseCodes(400);
                br.getPage("https://www.vessel.com/");
                prepBRAPI(br);
                // br.setCookie(MAINPAGE, "device_id", "f195cbafff11d9b83ce4cb07590e0735");
                // br.setCookie(MAINPAGE, "session_id", "a5c8be2f221bcb72590ef8c03252c50c");
                // br.setCookie(MAINPAGE, "_ga", "GA1.2.160339541.1436797816");
                // br.setCookie(MAINPAGE, "signup_flow", "0");
                // br.setCookie(MAINPAGE, "_gat", "1");
                br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
                // br.getPage("https://www.vessel.com/api/account/plan_price?track_id=339");
                // br.getPage("https://www.vessel.com/api/content/items?type=tag&tag_type=category&sort=title");
                br.postPageRaw("https://www.vessel.com/api/account/login", "{\"user_key\":\"" + account.getUser() + "\",\"password\":\"" + account.getPass() + "\",\"type\":\"password\",\"client_id\":\"web\"}");
                if (br.getHttpConnection().getResponseCode() == 403) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
                final LinkedHashMap<String, Object> user = (LinkedHashMap<String, Object>) entries.get("user");
                final LinkedHashMap<String, Object> customer = (LinkedHashMap<String, Object>) entries.get("customer");
                final LinkedHashMap<String, Object> plan = (LinkedHashMap<String, Object>) customer.get("plan");

                final String user_token = (String) entries.get("user_token");
                final String parrot_id = (String) entries.get("parrot_id");
                final String ga_id = (String) entries.get("ga_id");

                final String first_name = (String) user.get("first_name");
                final String last_name = (String) user.get("last_name");
                final String username = (String) user.get("username");
                final String gender = (String) user.get("gender");
                final String birth_date = (String) user.get("birth_date");

                final String status = (String) customer.get("status");
                final boolean is_paid = ((Boolean) customer.get("is_paid")).booleanValue();

                final String id = Long.toString(jd.plugins.hoster.DummyScriptEnginePlugin.toLong(plan.get("id"), -1));
                final String name = (String) plan.get("name");

                if (!name.equals("vip") || !is_paid) {
                    final String lang = System.getProperty("user.language");
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername/Passwort oder nicht unterstützter Account Typ!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or unsupported account type!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                final String jsonCookie = "{\"first_name\":\"" + first_name + "\",\"last_name\":\"" + last_name + "\",\"username\":\"" + username + "\",\"gender\":\"" + gender + "\",\"birth_date\":\"" + birth_date + "\",\"ga_id\":\"" + ga_id + "\",\"parrot_id\":\"" + parrot_id + "\",\"status\":\"" + status + "\",\"plan\":{\"id\":" + id + "}}";
                br.setCookie(MAINPAGE, "user_token", user_token);
                br.setCookie(MAINPAGE, "local_user", Encoding.urlEncode(jsonCookie));
                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = br.getCookies(MAINPAGE);
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

    public static void prepBRAPI(final Browser br) {
        br.getHeaders().put("Accept-Encoding", "gzip, deflate");
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
    }

    @SuppressWarnings({ "deprecation", "unchecked" })
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(this.br, account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        br.getPage("/account");
        br.getPage("/api/account/profile?with_customer_info=1");
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
        entries = (LinkedHashMap<String, Object>) entries.get("subscription");
        final String status = (String) entries.get("status");
        final String expire = (String) entries.get("current_period_end");
        ai.setUnlimitedTraffic();
        if (expire == null || !"active".equals(status)) {
            final String lang = System.getProperty("user.language");
            if ("de".equalsIgnoreCase(lang)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername/Passwort oder nicht unterstützter Account Typ!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or unsupported account type!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        } else {
            ai.setValidUntil(TimeFormatter.getMilliSeconds(expire, "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH));
        }
        maxPrem.set(ACCOUNT_PREMIUM_MAXDOWNLOADS);
        try {
            account.setType(AccountType.PREMIUM);
            account.setMaxSimultanDownloads(maxPrem.get());
            account.setConcurrentUsePossible(true);
        } catch (final Throwable e) {
            /* not available in old Stable 0.9.581 */
        }
        ai.setStatus("Premium account");
        account.setValid(true);
        return ai;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(this.br, account, false);
        br.setFollowRedirects(false);
        br.getPage(link.getDownloadURL());
        doFree(link);
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /* workaround for free/premium issue on stable 09581 */
        return maxPrem.get();
    }

    @Override
    public String getDescription() {
        return "JDownloader's vessel plugin helps downloading videoclips from vessel.com.";
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), FAST_LINKCHECK, JDL.L("plugins.hoster.VesselCom.FastLinkcheck", "Enable fast linkcheck?\r\nNOTE: If enabled, links will appear faster but filesize won't be shown before downloadstart.")).setDefaultValue(false));
        final Iterator<Entry<String, String[]>> it = formats.entrySet().iterator();
        while (it.hasNext()) {
            /*
             * Format-name:videoCodec, videoBitrate, videoResolution, audioCodec, audioBitrate
             */
            String usertext = "Load ";
            final Entry<String, String[]> videntry = it.next();
            final String internalname = videntry.getKey();
            final String[] vidinfo = videntry.getValue();
            final String videoCodec = vidinfo[0];
            final String videoBitrate = vidinfo[1];
            final String videoResolution = vidinfo[2];
            final String audioCodec = vidinfo[3];
            final String audioBitrate = vidinfo[4];
            if (videoCodec != null) {
                usertext += videoCodec + " ";
            }
            if (videoBitrate != null) {
                usertext += videoBitrate + " ";
            }
            if (videoResolution != null) {
                usertext += videoResolution + " ";
            }
            if (audioCodec != null || audioBitrate != null) {
                usertext += "with audio ";
                if (audioCodec != null) {
                    usertext += audioCodec + " ";
                }
                if (audioBitrate != null) {
                    usertext += audioBitrate;
                }
            }
            if (usertext.endsWith(" ")) {
                usertext = usertext.substring(0, usertext.lastIndexOf(" "));
            }
            final ConfigEntry vidcfg = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), internalname, JDL.L("plugins.hoster.VesselCom.ALLOW_" + internalname, usertext)).setDefaultValue(true);
            getConfig().addEntry(vidcfg);
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}