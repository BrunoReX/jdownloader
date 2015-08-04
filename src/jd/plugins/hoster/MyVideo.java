//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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

import java.util.HashMap;
import java.util.Map.Entry;

import org.jdownloader.captcha.v2.challenge.keycaptcha.KeyCaptchaShowDialogTwo;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.download.DownloadInterface;
import jd.utils.JDHexUtils;
import jd.utils.JDUtilities;

// Altes Decrypterplugin bis Revision 14394
@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "myvideo.de" }, urls = { "fromDecrypter://(www\\.)?myvideo\\.(de|at)/.+" }, flags = { 32 })
public class MyVideo extends PluginForHost {

    private String               CLIPURL         = null;
    private String               CLIPPATH        = null;
    private String               SWFURL          = null;
    private String               KEY             = "Yzg0MDdhMDhiM2M3MWVhNDE4ZWM5ZGM2NjJmMmE1NmU0MGNiZDZkNWExMTRhYTUwZmIxZTEwNzllMTdmMmI4Mw==";
    private static final boolean rtmpe_supported = false;
    private static final String  type_watch      = "http://(www\\.)?myvideo\\.de/watch/\\d+(/\\w+)?";
    private static final String  type_special    = "http://(www\\.)?myvideo\\.de/[a-z0-9\\-]+/[a-z0-9\\-]+/[a-z0-9\\-]+\\-m\\-\\d+";
    public static final String   age_restricted  = "Dieser Film ist für Zuschauer unter \\d+ Jahren nicht geeignet";

    public MyVideo(PluginWrapper wrapper) {
        super(wrapper);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void correctDownloadLink(DownloadLink link) throws Exception {
        link.setUrlDownload(link.getDownloadURL().replaceFirst("myvideo.at/", "myvideo.de/").replaceFirst("fromDecrypter", "http"));
    }

    /* HbbTV also available */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        setBrowserExclusive();
        br.setFollowRedirects(false);
        br.setCustomCharset("utf8");
        br.getPage(downloadLink.getDownloadURL());
        final String redirect = br.getRedirectLocation();
        if (redirect != null) {
            if (redirect.equals("http://www.myvideo.de/")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (redirect.matches("http://(www\\.)?myvideo\\.de/channel/.+")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            br.getPage(redirect);
        }
        br.setFollowRedirects(true);

        if (br.containsHTML(age_restricted)) {
            String ageCheck = br.getRegex("class=\'btnMiddle\'><a href=\'(/iframe.*?)\'").getMatch(0);
            if (ageCheck != null) {
                br.getPage("http://www.myvideo.de" + Encoding.htmlDecode(ageCheck));
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        }

        String filename = getFilename(this.br);
        /* get encUrl */
        String vid = null;
        String next = null;
        String values = br.getRegex("flashvars=\\{(.*?)\\}").getMatch(0);
        HashMap<String, String> p = new HashMap<String, String>();
        if (values != null) {
            for (String[] tmp : new Regex(values == null ? "NPE" : values, "(.*?):\'(.*?)\',").getMatches()) {
                if (tmp.length != 2) {
                    continue;
                }
                p.put(tmp[0], tmp[1]);
            }
            if (p.isEmpty() || !p.containsKey("_encxml") || !p.containsKey("ID")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            next = Encoding.htmlDecode(p.get("_encxml")) + "?";
            p.remove("_encxml");
            for (Entry<String, String> valuePair : p.entrySet()) {
                if (!next.endsWith("?")) {
                    next = next + "&";
                }
                next = next + valuePair.getKey() + "=" + valuePair.getValue();
            }
            vid = p.get("ID");
        } else {
            /* Maybe special type - let's build the xml via the other way */
            vid = new Regex(downloadLink.getDownloadURL(), "/watch/(\\d+)").getMatch(0);
            if (vid == null) {
                vid = new Regex(downloadLink.getDownloadURL(), "(\\d+)$").getMatch(0);
            }
            next = "http://www.myvideo.de/dynamic/get_player_video_xml.php?flash_playertype=D&autorun=yes&ID=" + vid + "&ds=1";
        }
        SWFURL = br.getRegex("(SWFObject|embedSWF)\\(\'(.*?)\',").getMatch(1);
        SWFURL = SWFURL == null ? "http://is4.myvideo.de/de/player/mingR11q/ming.swf" : SWFURL;
        br.getPage(next + "&domain=www.myvideo.de");
        String input = br.getRegex("_encxml=(\\w+)").getMatch(0);
        if (input == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        input = input.replaceAll("%0D%0A", "").trim();
        String result;
        try {
            result = decrypt(input, vid);
        } catch (Throwable e) {
            e.printStackTrace();
            return AvailableStatus.UNCHECKABLE;
        }
        System.out.println(result);
        CLIPURL = new Regex(result, "connectionurl=\'(.*?)\'").getMatch(0);
        CLIPPATH = new Regex(result, "source=\'(.*?)\'").getMatch(0);
        if (CLIPURL.equals("") || CLIPPATH == null) {
            CLIPURL = new Regex(result, "path=\'(.*?)\'").getMatch(0);
        }
        if (CLIPURL == null || CLIPPATH.equals("")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String ext = new Regex(CLIPPATH, "(\\.\\w{3})$").getMatch(0);
        if (!CLIPPATH.matches("(\\w+):(\\w+)/(\\w+)/(\\d+)") && ext != null && !CLIPURL.startsWith("http")) {
            CLIPPATH = CLIPPATH.replace(ext, "");
            if (ext.startsWith(".")) {
                CLIPPATH = ext.replace(".", "") + ":" + CLIPPATH;
            } else {
                CLIPPATH = ext + ":" + CLIPPATH;
            }
        }
        ext = ext == null ? ".mp4" : ext;
        if (filename == null) {
            filename = "unknown_myvideo_title__ID(" + p.get("ID") + ")_" + System.currentTimeMillis();
        }
        filename = filename.replaceAll("\t", "").trim() + ext;
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename));
        /* filesize */
        if (CLIPURL.startsWith("http://")) {
            final String hq_url = CLIPPATH.replace(".flv", ".flv_hq.flv");
            Browser br2 = br.cloneBrowser();
            URLConnectionAdapter con = null;
            try {
                con = br2.openHeadConnection(CLIPURL + hq_url);
                /* Check if hq is actually available (only possible way is to try it) */
                if (con.getContentType().contains("html")) {
                    con = br2.openHeadConnection(CLIPURL + CLIPPATH);
                }
                if (!con.getContentType().contains("html")) {
                    downloadLink.setDownloadSize(con.getLongContentLength());
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
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

    public static String getFilename(final Browser br) {
        String filename = null;
        if (br.getURL().matches(type_watch)) {
            filename = br.getRegex("name=\\'subject_title\\' value=\\'([^\\'<]+)").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("name=\\'title\\' content=\\'(.*?)(Video)? \\-? (Film|Musik|TV Serie|MyVideo)").getMatch(0);
            }
        } else {
            /* Maybe musicvideo */
            filename = br.getRegex("<title>([^<>\"]*?) \\- Kostenlos in voller L\\&auml;nge \\- MyVideo</title>").getMatch(0);
            /* Maybe movie */
            if (filename == null) {
                filename = br.getRegex("<title>([^<>\"]*?)\\– Musikvideo kostenlos auf MyVideo ansehen\\!</title>").getMatch(0);
            }
            /* Maybe series */
            if (filename == null) {
                filename = br.getRegex("<title>([^<>\"]*?)\\- kostenlos auf MyVideo ansehen\\!</title>").getMatch(0);
            }
        }
        if (filename == null) {
            filename = br.getURL();
            if (filename != null) {
                filename = filename.substring(filename.lastIndexOf("/") + 1);
            }
        }
        return filename;
    }

    private String decrypt(String cipher, String id) {
        String key = JDHash.getMD5(Encoding.Base64Decode(KEY) + JDHash.getMD5(id));
        byte[] ciphertext = JDHexUtils.getByteArray(cipher);
        // plugin should be loaded first,
        JDUtilities.getPluginForDecrypt("linkcrypt.ws");
        org.jdownloader.captcha.v2.challenge.keycaptcha.KeyCaptchaShowDialogTwo arkfour = new org.jdownloader.captcha.v2.challenge.keycaptcha.KeyCaptchaShowDialogTwo();
        /* CHECK: we should always use getBytes("UTF-8") or with wanted charset, never system charset! */
        byte[] plain = arkfour.D(key.getBytes(), ciphertext);
        /* CHECK: we should always use new String (bytes,charset) to avoid issues with system charset and utf-8 */
        return Encoding.htmlDecode(new String(plain));
    }

    private void download(final DownloadLink downloadLink) throws Exception {
        if (CLIPURL.startsWith("rtmp")) {
            dl = new RTMPDownload(this, downloadLink, CLIPURL);
            setupRTMPConnection(dl);
            ((RTMPDownload) dl).startDownload();
        } else if (CLIPURL.startsWith("http")) {
            br.getHeaders().put("Referer", SWFURL);
            br.getHeaders().put("x-flash-version", "10,3,183,7");
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, CLIPURL + CLIPPATH, true, 1);
            if (dl.getConnection().getContentType().contains("html")) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        } else {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
    }

    @Override
    public String getAGBLink() {
        return "http://www.myvideo.de/AGB";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        download(downloadLink);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }

    private void setupRTMPConnection(final DownloadInterface dl) {
        jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();
        String app = CLIPURL.replaceAll("\\w+://[\\w\\.]+/", "");
        /* Ensure we're using the right protocol */
        if (!rtmpe_supported) {
            CLIPURL = CLIPURL.replaceAll("rtmpe://", "rtmp://");
            rtmp.setProtocol(0);
        }
        rtmp.setPlayPath(CLIPPATH);
        /* = usually "myvideo3" + maybe "?token=" + token */
        rtmp.setApp(app);
        rtmp.setUrl(CLIPURL);
        rtmp.setSwfVfy(SWFURL);
        rtmp.setFlashVer("WIN 16,0,0,305");
        rtmp.setResume(true);
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        return true;
    }
}