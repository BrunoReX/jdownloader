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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
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
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "bego.cc", "ctdisk.com" }, urls = { "http://(www\\.)?((ctdisk|400gb|pipipan|t00y)\\.com|bego\\.cc)/file/\\d+", "REGEX_NOT_POSSIBLE_RANDOM-asdfasdfsadfsdgfd32423" }, flags = { 2, 0 })
public class CtDiskCom extends PluginForHost {

    private static final String DLLINKREGEX2  = ">电信限速下载</a>[\t\n\r ]+<a href=\"(http://.*?)\"";
    private static final String MAINPAGE      = "http://www.bego.cc/";
    private static Object       LOCK          = new Object();
    private static Object       linkcheckLOCK = new Object();

    public CtDiskCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.bego.cc/premium.php");
        this.setStartIntervall(5 * 1000l);
    }

    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replaceAll("((ctdisk|pipipan|t00y)\\.com|bego\\.cc|400gb\\.com)/", "bego.cc/"));
    }

    public void prepBrowser(final Browser br) {
        // define custom browser headers and language settings.
        br.getHeaders().put("Accept-Language", "en-gb, en;q=0.9");
        br.setConnectTimeout(120000);
        br.setReadTimeout(120000);
    }

    @Override
    public String rewriteHost(String host) {
        if ("ctdisk.com".equals(getHost()) || "400gb.com".equals(getHost())) {
            if (host == null || "ctdisk.com".equals(host) || "400gb.com".equals(host)) {
                return "bego.cc";
            }
        }
        return super.rewriteHost(host);
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        br = new Browser();
        this.setBrowserExclusive();
        prepBrowser(br);
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (br.containsHTML("(>提示：本文件已到期。成为VIP后才能下载所有文件|<title>成为VIP即可下载全部视频和文件</title>|>注意：高级VIP可下载所有文件|color=\"#FF0000\" face=\"黑体\">点击立即成为VIP</font>|>Woops\\!找不到文件 \\- 免费高速下载|>对不起，这个文件已到期或被删除。您可以注册城通会)")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = null;
        if (br.getURL().contains("bego.cc/") || br.getURL().contains("pipipan.com/")) {
            filename = br.getRegex("<div class=\"file_title\">([^<>\"]*?)</div>").getMatch(0);
        } else {
            filename = br.getRegex("<div class=\"top_title\"[^>]+>(.*?)<").getMatch(0);
        }
        if (filename == null) {
            filename = br.getRegex("<title>(.*?) \\- 免费高速下载").getMatch(0);
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(Encoding.htmlDecode(filename.trim()));
        String filesize = br.getRegex("\">\\(([\\d\\,\\.]+ (KB|MB|GB))\\)</span><").getMatch(0);
        if (filesize == null) {
            filesize = br.getRegex("([\\d\\,\\.]+ (KB|MB|GB))").getMatch(0);
        }
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        return AvailableStatus.TRUE;
    }

    public void doFree(final DownloadLink downloadLink, final boolean premium, final int maxchunks) throws Exception, PluginException {
        final boolean pluginbroken = true;
        if (pluginbroken) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getPage(downloadLink.getDownloadURL());
        final String fid = new Regex(downloadLink.getDownloadURL(), "(\\d+)$").getMatch(0);
        String dllink = checkDirectLink(downloadLink, "directlink");
        if (dllink == null) {
            dllink = findDllink();
            if (dllink != null) {
                dllink = Encoding.Base64Decode(Encoding.htmlDecode(dllink));
            }
        }
        if (dllink == null) {
            try {
                Browser ad = br.cloneBrowser();
                ad.getHeaders().put("Accept", "*/*");
                /* Without this, site won't accept the captcha! */
                ad.cloneBrowser().getPage("/min/g=css1?v=0.002");
                ad.cloneBrowser().getPage("/min/g=css1?v=0.003");
                ad.cloneBrowser().getPage("/min/g=js_1?v=0.001");
                ad.cloneBrowser().getPage("/min/g=js_1?v=0.002");
                ad.cloneBrowser().getPage("/adm.js");
                ad.cloneBrowser().getPage("/advview2013.php?ad_pos=0");
            } catch (final Throwable e) {
            }
            final Form free = br.getFormbyProperty("name", "user_form");
            String captcha = br.getRegex("((https?://[^/]+)?/randcodeV2([A-Za-z0-9\\-_]+)?\\.php\\?fid=" + fid + "[^<>\"]*?\\&rand=)").getMatch(0);
            if (free == null || captcha == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            /*
             * Check hash_key handling if ever plugin is broken - if it does not work with the correct hash_key, check if it works via
             * browser with adblocker enabled!
             */
            String hash_key = br.getRegex("\\$\\(\"#hash_key\"\\)\\.val\\(\"([^<>\"]*?)\"\\)").getMatch(0);
            if (hash_key == null) {
                hash_key = br.getRegex("name=\"hash_key\" value=\"([^<>\"]*?)\"").getMatch(0);
            }
            if ((hash_key == null || hash_key.equals("")) && free.hasInputFieldByName("hash_info")) {
                String tmp = free.getInputFieldByName("hash_info").getValue();
                if (tmp != null) {
                    tmp = Encoding.htmlDecode(tmp);
                    hash_key = Encoding.Base64Decode(tmp);
                }
            }
            if (free.hasInputFieldByName("page_content")) {
                final String p1 = br.toString();
                final String p2 = LZString.compressToBase64(p1);
                final String p3 = Encoding.urlEncode(p2);
                free.put("page_content", p3);
            } else {
                // if (hash_key == null) {
                // try {
                // final Browser brc = br.cloneBrowser();
                // brc.getPage("http://www.bego.cc/getdownloadkey.php?id=" + fid);
                // final String b64 = Encoding.Base64Decode(brc.getRegex("eval\\(\\$\\.base64\\.decode\\(\"(.*?)\"\\)\\);").getMatch(0));
                // hash_key = new Regex(b64, "\\$\\(\"#hash_key\"\\)\\.val\\(\"([^<>\"]*?)\"\\)").getMatch(0);
                // } catch (final Throwable e) {
                // }
                // }
                // if (hash_key != null) {
                // free.put("hash_key", hash_key);
                // }
            }
            captcha += "0." + new Random().nextInt(999999999);
            String code = getCaptchaCode(captcha, downloadLink);
            free.put("randcode", code);
            // other test
            Browser ad = br.cloneBrowser();
            ad.getHeaders().put("Accept", "*/*");
            ad.cloneBrowser().getPage("http://bdimg.share.baidu.com/static/js/shell_v2.js?cdnversion=394683");
            ad.cloneBrowser().getPage("http://hm.baidu.com/h.js?74590c71164d9fba556697bee04ad65c");
            // send form
            br.submitForm(free);
            if (br.containsHTML(">验证码输入错误或<b>广告被拦截</b>")) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            // and again
            ad = br.cloneBrowser();
            ad.getHeaders().put("Accept", "*/*");
            ad.cloneBrowser().getPage("http://www.bego.cc/advview2013.php?ad_pos=1");
            ad.cloneBrowser().getPage("http://www.bego.cc/adm.js");
            ad.cloneBrowser().getPage("http://www.bego.cc/advview2013.php?ad_pos=2");
            ad.cloneBrowser().getPage("http://www.bego.cc/advview2013.php?ad_pos=3");

            if (!br.getURL().matches(".+/downhtml/\\d+/.+")) {
                String continueLink = br.getRegex("\"\\&downlink=(/downhtml/[^<>\"]*?)\";").getMatch(0);
                if (continueLink == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.getPage(continueLink);
            }
            if (dllink == null) {
                dllink = findDllink();
                if (dllink == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                dllink = Encoding.Base64Decode(Encoding.htmlDecode(dllink));
            }
        }
        if (dllink == null || !dllink.startsWith("http")) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        // Check if link is working. If not try mirror. Limits can be extended/skipped using this method :D
        boolean failed = false;
        URLConnectionAdapter con = null;
        final Browser br2 = br.cloneBrowser();
        try {
            con = br2.openGetConnection(dllink);
        } catch (Exception e) {
            failed = true;
        }
        if (failed || con.getContentType().contains("html") || con.getResponseCode() == 503) {
            dllink = br.getRegex(DLLINKREGEX2).getMatch(0);
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Too many simultan downloads", 5 * 60 * 1000l);
            }
        }
        if (con != null) {
            con.disconnect();
        }
        try {
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, maxchunks);
        } catch (Exception e) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Too many simultan downloads", 5 * 60 * 1000l);
        }
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setProperty("directlink", dllink);
        dl.startDownload();
    }

    private String findDllink() {
        String dllink = br.getRegex("<a class=\"local\" href=\"([^\"]+)\" id=\"local_free\">").getMatch(0);
        if (dllink == null) {
            dllink = br.getRegex("<a class=\"local\" href=\"([^\"]+)\"").getMatch(0);
        }
        return dllink;
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        try {
            login(account, false);
            br.getPage("/mydisk.php");
            /* Only re-login if cookies are not valid anymore */
            if (br.containsHTML("alert\\(\"请先 登录 或 注册会员后再继续使用本功能")) {
                login(account, true);
            }
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        ai.setUnlimitedTraffic();
        account.setValid(true);
        ai.setStatus("Premium Account");
        return ai;
    }

    @Override
    public String getAGBLink() {
        return "http://www.400gb.com/help.php?item=service";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        // Works best this way. Maximum that worked for me was 6
        return -1;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, false, 1);
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account, false);
        String dllink = checkDirectLink(link, "directpremlink");
        if (dllink == null) {
            br.getPage(link.getDownloadURL());
            final String downHTML = br.getRegex("(\\'|\")(/downhtml/[^<>\"]*?\\.html)(\\'|\")").getMatch(1);
            if (downHTML == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.getPage("http://www.bego.cc" + downHTML);
            final String vipLinks = br.getRegex("viplist\">(.*?)<table class=\"table table").getMatch(0);
            if (vipLinks == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dllink = new Regex(vipLinks, "<a href=\"(.*?)\"").getMatch(0);
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = Encoding.Base64Decode(dllink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, -5);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setProperty("directpremlink", dllink);
        dl.startDownload();
    }

    // do not add @Override here to keep 0.* compatibility
    public boolean hasCaptcha() {
        return true;
    }

    @SuppressWarnings("unchecked")
    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            // Load cookies
            br.setCookiesExclusive(true);
            prepBrowser(br);
            final Object ret = account.getProperty("cookies", null);
            boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
            if (acmatch) {
                acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
            }
            if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                if (cookies.containsKey("pubcookie") && account.isValid()) {
                    for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                        final String key = cookieEntry.getKey();
                        final String value = cookieEntry.getValue();
                        this.br.setCookie(MAINPAGE, key, value);
                    }
                    return;
                }
            }
            br.setFollowRedirects(true);
            br.getPage("http://www.bego.cc/index.php?item=account&action=login");
            String hash = br.getRegex("name=\"formhash\" value=\"(.*?)\"").getMatch(0);
            if (hash == null) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
            String postData = "item=account&action=login&task=login&ref=mydisk.php&formhash=" + Encoding.urlEncode(hash) + "&username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()) + "&remember=on&btnToLogin.x=" + new Random().nextInt(100) + "&btnToLogin.y=" + new Random().nextInt(100);
            final String captchaURL = br.getRegex("\"(/randcodeV2_login\\.php\\?\\d+)\"").getMatch(0);
            if (captchaURL != null) {
                final DownloadLink dummy = new DownloadLink(this, "Account login", "bego.cc", null, true);
                final String code = getCaptchaCode(captchaURL, dummy);
                postData += "&randcodeV2=" + Encoding.urlEncode(code);
            }
            br.postPage("/index.php", postData);
            if (br.getCookie(MAINPAGE, "pubcookie") == null || "deleted".equals(br.getCookie(MAINPAGE, "pubcookie"))) {
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            // Save cookies
            final HashMap<String, String> cookies = new HashMap<String, String>();
            final Cookies add = this.br.getCookies(MAINPAGE);
            for (final Cookie c : add.getCookies()) {
                cookies.put(c.getKey(), c.getValue());
            }
            account.setProperty("name", Encoding.urlEncode(account.getUser()));
            account.setProperty("pass", Encoding.urlEncode(account.getPass()));
            account.setProperty("cookies", cookies);
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
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