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
import java.text.DecimalFormat;

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

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "myspass.de", "tvtotal.prosieben.de" }, urls = { "http://(?:www\\.)?myspass\\.de/(?:myspass/)?shows/(?:tv|web)shows/[a-z0-9\\-_]+/[A-Za-z0-9\\-_]+/\\d+", "http://tvtotal\\.prosieben\\.de/videos/.*?/\\d+/" }, flags = { 0, 0 })
public class MySpassDe extends PluginForHost {

    public MySpassDe(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String DLLINK = null;

    @Override
    public String getAGBLink() {
        return "http://www.myspass.de/myspass/kontakt/";
    }

    /*
     * Example final url (18.05.2015):
     * http://x3583brainc11021.s.o.l.lb.core-cdn.net/secdl/78de6150fffffffffff1f136aff77d61/55593149/11021brainpool/ondemand
     * /3583brainpool/163840/myspass2009/14/660/10680/18471/18471_61.mp4
     */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final String fid = new Regex(downloadLink.getDownloadURL(), "(\\d+)/?$").getMatch(0);
        downloadLink.setLinkID(fid);
        br.getPage("http://www.myspass.de/myspass/includes/apps/video/getvideometadataxml.php?id=" + fid + "&0." + System.currentTimeMillis());
        if (br.containsHTML("<url_flv><\\!\\[CDATA\\[\\]\\]></url_flv>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final DecimalFormat df = new DecimalFormat("00");
        String filename = getXML("format") + " - S" + df.format(Integer.parseInt(getXML("season"))) + "E" + df.format(Integer.parseInt(getXML("episode"))) + " - " + getXML("title");
        DLLINK = getXML("url_flv");
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
            con = openConnection(br2, DLLINK);
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
        /* Workaround for old downloadcore bug that can lead to incomplete files */
        br.getHeaders().put("Accept-Encoding", "identity");
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private String getXML(final String parameter) {
        return br.getRegex("<" + parameter + "><\\!\\[CDATA\\[(.*?)\\]\\]></" + parameter + ">").getMatch(0);
    }

    private URLConnectionAdapter openConnection(final Browser br, final String directlink) throws IOException {
        URLConnectionAdapter con;
        if (isJDStable()) {
            con = br.openGetConnection(directlink);
        } else {
            con = br.openHeadConnection(directlink);
        }
        return con;
    }

    private boolean isJDStable() {
        return System.getProperty("jd.revision.jdownloaderrevision") == null;
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
