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

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "video.mediaset.it" }, urls = { "https?://(?:www\\.)?video\\.mediaset\\.it/(video/.*?\\.html|player/playerIFrame\\.shtml\\?id=\\d+)" }, flags = { 0 })
public class VideoMediasetIt extends PluginForHost {

    public VideoMediasetIt(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String DLLINK = null;

    @Override
    public String getAGBLink() {
        return "http://www.licensing.mediaset.it/";
    }

    private static final String TYPE_EMBED   = "https?://(?:www\\.)?video\\.mediaset\\.it/player/playerIFrame\\.shtml\\?id=\\d+";
    private static final String TYPE_NORMAL  = "https?://(?:www\\.)?video\\.mediaset\\.it/video/.+";
    private boolean             dlImpossible = false;

    // Important info: Can only handle normal videos, NO
    // "Microsoft Silverlight forced" videos!
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        String streamID = new Regex(downloadLink.getDownloadURL(), "video\\.mediaset\\.it/video/[^<>/\"]*?/[^<>/\"]*?/(\\d+)/").getMatch(0);
        if (streamID == null) {
            streamID = new Regex(downloadLink.getDownloadURL(), "(\\d+)\\.html$").getMatch(0);
        }
        if (streamID == null) {
            /* Whatever the user added it is probably not a video! */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.setReadTimeout(3 * 60 * 1000);
        br.getPage(downloadLink.getDownloadURL());
        /* 2016-06-16 TODO: Fix support for embedded URLs */
        if (downloadLink.getDownloadURL().matches(TYPE_EMBED)) {
            downloadLink.setName(new Regex(downloadLink.getDownloadURL(), "(\\d+)$").getMatch(0));
            if (!br.getURL().matches(TYPE_NORMAL)) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            downloadLink.setUrlDownload(br.getURL());
        }
        if (this.br.getHttpConnection().getResponseCode() == 404 || br.containsHTML(">Il video che stai cercando non")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (br.containsHTML("silverlight/playerSilverlight\\.js\"")) {
            downloadLink.getLinkStatus().setStatusText("JDownloader can't download MS Silverlight videos!");
            return AvailableStatus.TRUE;
        }
        String filename = br.getRegex("content=\"([^<>]*?) \\| Video Mediaset\" name=\"title\"").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("class=\"title\">([^<>\"]+)<").getMatch(0);
        }
        if (filename == null) {
            filename = br.getRegex("<h2 class=\"titleWrap\">([^<>]*?)</h2>").getMatch(0);
        }
        if (filename == null) {
            filename = br.getRegex("<title>([^<>]*?)\\- Video Mediaset</title>").getMatch(0);
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        filename = Encoding.htmlDecode(filename.trim()).replace("\"", "'");
        filename = encodeUnicode(filename);
        /** New way, thx to: http://userscripts.org/scripts/review/151516 */
        // E.g. original request:
        // http://cdnselector.xuniplay.fdnames.com/GetCDN.aspx?streamid=123456&format=json&callback=jQuery5456457_45747847&_=36747457
        br.getPage("http://cdnselector.xuniplay.fdnames.com/GetCDN.aspx?streamid=" + streamID + "&format=json");
        final String videoList = br.getRegex("\"videoList\":\\[(.*?)\\]").getMatch(0);
        if (videoList == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final String[] dllinks = new Regex(videoList, "\"(http://[^<>\"]*?)\"").getColumn(0);
        if (dllinks != null && dllinks.length != 0) {
            final int length = dllinks.length;
            if (length >= 3) {
                DLLINK = dllinks[2];
            } else if (length >= 2) {
                DLLINK = dllinks[1];
            } else {
                DLLINK = dllinks[0];
            }
        }
        if (DLLINK == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (DLLINK.contains("Error400") || br.containsHTML("/Cartello_NotAvailable\\.wmv")) {
            downloadLink.getLinkStatus().setStatusText("JDownloader can't download this video (either blocked in your country or MS Silverlight)");
            downloadLink.setName(filename + ".mp4");
            return AvailableStatus.TRUE;
        }
        DLLINK = Encoding.htmlDecode(DLLINK);
        String ext = DLLINK.substring(DLLINK.lastIndexOf("."));
        if (ext == null || ext.length() > 5) {
            ext = ".mp4";
        }
        downloadLink.setFinalFileName(filename + ext);
        final Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br2.openGetConnection(DLLINK);
            if (!con.getContentType().contains("html")) {
                if (con.getLongContentLength() < 200) {
                    dlImpossible = true;
                } else {
                    downloadLink.setDownloadSize(con.getLongContentLength());
                }
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
    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        if (br.containsHTML("silverlight/playerSilverlight\\.js\"")) {
            throw new PluginException(LinkStatus.ERROR_FATAL, "JDownloader can't download MS Silverlight videos!");
        }
        if (DLLINK.contains("Error400") || br.containsHTML("/Cartello_NotAvailable\\.wmv")) {
            throw new PluginException(LinkStatus.ERROR_FATAL, "JDownloader can't download this video (either blocked in your country or MS Silverlight)");
        }
        if (dlImpossible) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error, try again later", 10 * 60 * 1000l);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    /** Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "⁄");
        output = output.replace("\\", "∖");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
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
