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

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@HostPlugin(revision = "$Revision: 30002 $", interfaceVersion = 3, names = { "pxhst.co" }, urls = { "http://(?:www\\.)?(?:pixhst\\.com|pxhst\\.co|avxhome\\.se)/pictures/\\d+" }, flags = { 0 })
public class PixhstCom extends antiDDoSForHost {

    public PixhstCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    // note: pixhst.com is parked.
    @Override
    public void correctDownloadLink(DownloadLink link) throws Exception {
        // some times pxhost redirects to avxhome other times not!
        final String l = link.getPluginPatternMatcher().replaceFirst("(pixhst\\.com)/", "pxhst.co/");
        link.setPluginPatternMatcher(l);
        link.setContentUrl(l);
    }

    private String dllink = null;

    @Override
    public String getAGBLink() {
        return "http://pixhst.com/dmca.html";
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        correctDownloadLink(downloadLink);
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        getPage(downloadLink.getDownloadURL());
        final String linkid = new Regex(downloadLink.getDownloadURL(), "(\\d+)$").getMatch(0);
        downloadLink.setLinkID(linkid);
        String filename = br.getRegex("<td colspan='3'>([^<>\"]*?)</td>").getMatch(0);
        if (filename != null) {
            /* Avoid duplicate filenames for different files */
            filename = Encoding.htmlDecode(filename);
            filename = filename.trim();
            filename = encodeUnicode(filename);
            filename = linkid + "_" + filename;
        } else {
            filename = linkid;
        }
        dllink = checkDirectLink(downloadLink, "directlink");
        if (dllink == null) {
            dllink = br.getRegex("<img [^>]+src='(https?://pi?xhst\\.[a-z0-9]+/[^<>\"]*?)'").getMatch(0);
        }
        if (filename == null || dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = Encoding.htmlDecode(dllink);
        String ext = dllink.substring(dllink.lastIndexOf("."));
        /* Make sure that we get a correct extension */
        if (ext == null || !ext.matches("\\.[A-Za-z0-9]{3,5}")) {
            // default extension
            ext = ".jpeg";
        }
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        downloadLink.setFinalFileName(filename);
        final Browser br2 = br.cloneBrowser();
        // In case the link redirects to the finallink
        br2.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            try {
                con = openAntiDDoSRequestConnection(br2, br.createHeadRequest(dllink));
            } catch (final BrowserException e) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (!con.getContentType().contains("html")) {
                downloadLink.setDownloadSize(con.getLongContentLength());
            } else {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            downloadLink.setProperty("directlink", dllink);
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
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    /* Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "Â¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "â�„");
        output = output.replace("\\", "âˆ–");
        output = output.replace("*", "#");
        output = output.replace("?", "Â¿");
        output = output.replace("!", "Â¡");
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
