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

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "proporn.com" }, urls = { "http://((www|de|fr|ru|es|it|jp|nl|pl|pt)\\.)?proporn\\.com/(video|embed)/\\d+" }, flags = { 0 })
public class ProPornCom extends PluginForHost {

    public ProPornCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String SKEY   = "VjN0NlJLRlkxMU9RNEo0Ug==";
    private String              DLLINK = null;

    @Override
    public String getAGBLink() {
        return "http://www.proporn.com/static/terms";
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload("http://www.proporn.com/video/" + new Regex(link.getDownloadURL(), "(\\d+)$").getMatch(0));
    }

    /* Similar sites: drtuber.com, proporn.com, viptube.com, tubeon.com, winporn.com */
    /* IMPORTANT: If the crypto stuff fails, use the mobile version of the sites to get uncrypted finallinks! */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.setCookie("http://proporn.com/", "lang", "en");
        br.getPage(downloadLink.getDownloadURL());
        if (br.containsHTML("class=\"nothing\" || <title>Page not found</title>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<title>([^<>\"]*?)\\- Free Porn Video").getMatch(0);
        final String h = br.getRegex("\\'h=([a-z0-9]+)").getMatch(0);
        final String t = br.getRegex("t=(\\d+)\\'").getMatch(0);
        final String vkey = br.getRegex("vkey=\\' \\+ \\'([a-z0-9]+)\\';").getMatch(0);
        br.getPage("http://www.proporn.com/player_config/?h=" + h + "&t=" + t + "&vkey=" + vkey + "&pkey=" + JDHash.getMD5(vkey + Encoding.Base64Decode(SKEY)) + "&aid=&domain_id=");
        DLLINK = br.getRegex("<video_file>(http://[^<>\"]*?)</video_file>").getMatch(0);
        if (DLLINK == null) {
            DLLINK = br.getRegex("<video_file><\\!\\[CDATA\\[(http://[^<>\"]*?)\\]\\]></video_file>").getMatch(0);
        }
        if (filename == null || DLLINK == null) {
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
