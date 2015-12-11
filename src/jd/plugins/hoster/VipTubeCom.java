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

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
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

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "viptube.com" }, urls = { "http://(www\\.)?viptube\\.com/(video|embed)/\\d+" }, flags = { 0 })
public class VipTubeCom extends PluginForHost {

    public VipTubeCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String DLLINK = null;

    @Override
    public String getAGBLink() {
        return "http://www.viptube.com/static/terms";
    }

    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload("http://www.viptube.com/video/" + new Regex(link.getDownloadURL(), "(\\d+)$").getMatch(0));
    }

    private static final String SKEY = "RXdxT0JRbUpETUpScmdYWg==";

    /* Similar sites: drtuber.com, proporn.com, viptube.com, tubeon.com, winporn.com */
    /*
     * IMPORTANT: If the crypto stuff fails, use the mobile version of the sites to get uncrypted finallinks! Also, registered users can see
     * uncrypted normal streamlinks!
     */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        DLLINK = null;
        final String url_filename = new Regex(downloadLink.getDownloadURL(), "viptube\\.com/(.+)").getMatch(0).replace("/", "_");
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(downloadLink.getDownloadURL());
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("\"title\":\"([^<>\"]*?)\"").getMatch(0);
        if (filename == null) {
            filename = url_filename;
        }
        DLLINK = this.br.getRegex("src=\"(http://[^/]+/mp4/[^<>\"]*?)\"").getMatch(0);
        if (DLLINK == null) {
            String cfgurl = br.getRegex("\\'(http://(www\\.)?viptube\\.com/player_config/[^<>\"]*?)\\'").getMatch(0);
            final String vkey = br.getRegex("vkey=([a-z0-9]+)").getMatch(0);
            if (cfgurl == null || vkey == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            cfgurl = Encoding.htmlDecode(cfgurl);
            br.getPage(cfgurl + "&pkey=" + JDHash.getMD5(vkey + Encoding.Base64Decode(SKEY)));
            final String[] qualities = { "hq_video_file", "video_file" };
            for (final String quality : qualities) {
                DLLINK = br.getRegex("<" + quality + "><\\!\\[CDATA\\[(http://[^<>\"]*?)\\]\\]></" + quality + ">").getMatch(0);
                if (DLLINK != null) {
                    break;
                }
            }
        }

        if (DLLINK == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        DLLINK = Encoding.htmlDecode(DLLINK);
        filename = filename.trim();
        String ext = DLLINK.substring(DLLINK.lastIndexOf("."));
        if (ext == null || ext.length() > 5) {
            ext = ".mp4";
        }
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename) + ext);
        final Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            try {
                con = br2.openGetConnection(DLLINK);
            } catch (final BrowserException e) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
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

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
