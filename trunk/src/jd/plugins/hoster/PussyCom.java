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
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "pussy.com" }, urls = { "http://(www\\.)?bangyoulater\\.com/(embed\\.php\\?id=\\d+|[a-z0-9]+/\\d+/.{1})|https?://(?:www\\.)?pussy\\.com/[A-Za-z0-9\\-_]+" }, flags = { 0 })
public class PussyCom extends PluginForHost {

    public PussyCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String DLLINK = null;

    @Override
    public String getAGBLink() {
        return "http://www.bangyoulater.com/dmca/";
    }

    @Override
    public String rewriteHost(String host) {
        if ("bangyoulater.com".equals(getHost())) {
            if (host == null || "bangyoulater.com".equals(host)) {
                return "pussy.com";
            }
        }
        return super.rewriteHost(host);
    }

    private static final String type_old = "http://(?:www\\.)?bangyoulater\\.com/(?:embed\\.php\\?id=\\d+|[a-z0-9]+/\\d+/.{1})";

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        DLLINK = null;
        this.setBrowserExclusive();
        if (downloadLink.getDownloadURL().matches(type_old)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (downloadLink.getDownloadURL().matches("http://pussy.com/([\\d]+|categories|dmca|favorites|myuploads|privacypolicy|search|terms|upload|view-2257)")) {
            logger.info("Unsupported/invalid link: " + downloadLink.getDownloadURL());
            return AvailableStatus.FALSE;
        }
        final String name_url = new Regex(downloadLink.getDownloadURL(), "pussy\\.com/(.+)").getMatch(0);

        downloadLink.setName(name_url);
        br.setFollowRedirects(true);
        br.getPage(downloadLink.getDownloadURL());
        // ">This video is no longer available" is always in the html
        if (br.getURL().equals("http://pussy.com/") || br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("class=\"thumbnail err\"")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("property=\"og:title\" content=\"([^<>\"]+)\"").getMatch(0);
        if (filename == null) {
            /* Fallback */
            filename = name_url;
        }
        DLLINK = br.getRegex("\\'(?:file|video)\\'[\t\n\r ]*?:[\t\n\r ]*?\\'(http[^<>\"]*?)\\'").getMatch(0);
        if (DLLINK == null) {
            DLLINK = br.getRegex("(?:file|url):[\t\n\r ]*?(?:\"|\\')(http[^<>\"]*?)(?:\"|\\')").getMatch(0);
        }
        if (DLLINK == null) {
            DLLINK = br.getRegex("videoUrl = \\'(http://[^<>\"]*?)\\'").getMatch(0);
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
        Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openHeadConnection(DLLINK);
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            return AvailableStatus.TRUE;
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
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
