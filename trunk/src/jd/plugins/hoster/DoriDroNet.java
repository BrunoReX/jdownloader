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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jd.PluginWrapper;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "doridro.net" }, urls = { "http://(www\\.)?doridrodecrypted\\.net/download/[^<>\"]*?\\.html" }, flags = { 0 })
public class DoriDroNet extends PluginForHost {

    public DoriDroNet(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return COOKIE_HOST;
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("doridrodecrypted.net/", "doridro.net/"));
    }

    private static Object       LOCK        = new Object();
    private static final String COOKIE_HOST = "http://doridro.net/";

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (this.br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<meta name=\"audio_title\" content=\"(.*?)\"").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("Title: ([^<>\"]+)<br").getMatch(0);
        }
        if (filename == null) {
            /* Fallback to url-filename */
            filename = new Regex(link.getDownloadURL(), "([^/]+)\\.html").getMatch(0);
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (filename.equals("")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        filename = Encoding.htmlDecode(filename.trim());
        if (this.br.containsHTML(">Download This Album<")) {
            filename += ".zip";
        } else {
            filename += ".mp3";
        }
        link.setFinalFileName(filename);
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings({ "unchecked", "deprecation" })
    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        synchronized (LOCK) {
            br.setCookiesExclusive(true);
            final Object ret = this.getPluginConfig().getProperty("cookies", null);
            if (ret != null && ret instanceof HashMap<?, ?>) {
                final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                    final String key = cookieEntry.getKey();
                    final String value = cookieEntry.getValue();
                    this.br.setCookie(COOKIE_HOST, key, value);
                }
            }
            br.getPage(downloadLink.getDownloadURL());
            if (br.containsHTML("(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)")) {
                for (int i = 0; i <= 5; i++) {
                    final Recaptcha rc = new Recaptcha(br, this);
                    rc.parse();
                    rc.load();
                    File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                    String c = getCaptchaCode("recaptcha", cf, downloadLink);
                    rc.setCode(c);
                    if (br.containsHTML("(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)")) {
                        continue;
                    }
                    break;
                }
                if (br.containsHTML("(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)")) {
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                }
                /** Save cookies to prevent more captchas */
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = this.br.getCookies(COOKIE_HOST);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
            }
        }
        String dllink = br.getRegex("Download Link</div><a href=\"(http://[^<>\"]+)\"").getMatch(0);
        if (dllink == null) {
            dllink = br.getRegex("\"(https?://dl\\.doridro\\.net/get/(http://[^<>\"]+))\"").getMatch(0);
        }
        if (dllink == null) {
            dllink = br.getRegex("\"(https?://doridro\\.net/file/[^<>\"]*?)\"").getMatch(0);
        }
        if (dllink == null) {
            /* Single mp3 download */
            dllink = this.br.getRegex("property=\"og:url\" content=\"(http[^<>\"]*?)\"").getMatch(0);
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            this.br.postPage(dllink, "action=nocaptchadown");
            dllink = getJson("down_link");
        }
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML("File Not Found\\.")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (br.containsHTML("Downloading this file is disabled by Administrator on Artists request")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Download not possible");
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    /**
     * Wrapper<br/>
     * Tries to return value of key from JSon response, from default 'br' Browser.
     *
     * @author raztoki
     * */
    private String getJson(final String key) {
        return jd.plugins.hoster.K2SApi.JSonUtils.getJson(br.toString(), key);
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        return true;
    }
}