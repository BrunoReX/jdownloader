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

import java.io.IOException;
import java.io.InputStream;

import jd.PluginWrapper;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "datafilehost.com" }, urls = { "https?://((www\\.)?datafilehost\\.com/(download\\-[a-z0-9]+\\.html|d/[a-z0-9]+)|www\\d+\\.datafilehost\\.com/d/[a-z0-9]+)" })
public class DataFileHostCom extends PluginForHost {

    // note: at this time download not possible? everything goes via 'download manager' which is just used to install adware/malware.

    private char[] FILENAMEREPLACES = new char[] { ' ', '_', '[', ']' };

    @Override
    public char[] getFilenameReplaceMap() {
        return FILENAMEREPLACES;
    }

    @Override
    public boolean isHosterManipulatesFilenames() {
        return true;
    }

    @Override
    public String filterPackageID(String packageIdentifier) {
        return packageIdentifier.replaceAll("([^a-zA-Z0-9]+)", "");
    }

    public DataFileHostCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.datafilehost.com/index.php?page=tos";
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:49.0) Gecko/20100101 Firefox/49.0");
        br.getHeaders().put("Cache-Control", "");
        br.getHeaders().put("Accept-Language", "de,de-DE;q=0.7,en;q=0.3");
        br.getHeaders().put("Connection", "keep-alive");
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("class=\"alert alert\\-danger\"") || this.br.containsHTML("The file that you are looking for is either|an invalid file name|has been removed due to|Please check the file name again and|>The file you requested \\(id [a-z0-9]+\\) does not exist.|>Invalid file ID.")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex(">\\s*File\\s*:\\s*(.*?)\\s*<").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("fileName=\"([^<>\"]+)\"").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("class=\"col-sm-3\">[\t\n\r ]*?<strong>File Name:</strong>[\t\n\r ]*?</div>[\t\n\r ]*?<div class=\"col-sm-9\">([^<>\"]+)</div>").getMatch(0);
            }
        }
        String filesize = br.getRegex(">\\s*Size\\s*:\\s*(.*?)\\s*<").getMatch(0);
        if (filesize == null) {
            filesize = br.getRegex("class=\"col-sm-3\">[\t\n\r ]*?<strong>File Size:</strong>[\t\n\r ]*?</div>[\t\n\r ]*?<div class=\"col-sm-9\">([^<>\"]+)</div>").getMatch(0);
            if (filename == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        link.setName(Encoding.htmlDecode(filename.trim()));
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        }
        return AvailableStatus.TRUE;
    }

    private void dummyLoad(final String url) throws IOException {
        final URLConnectionAdapter con = br.cloneBrowser().openGetConnection(url);
        try {
            if (con.isOK()) {
                final byte[] buf = new byte[32];
                final InputStream is = con.getInputStream();
                while (is.read(buf) != -1) {
                }
            }
        } finally {
            con.disconnect();
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        dummyLoad("//img.datafilehost.com/bg.jpg");
        dummyLoad("//img.datafilehost.com/menu.gif");
        dummyLoad("//img.datafilehost.com/download.png");
        dummyLoad("//img.datafilehost.com/check.png");
        final String fid = new Regex(downloadLink.getDownloadURL(), "([A-Za-z0-9]+)$").getMatch(0);
        final String dllink = br.getURL("/get.php?file=" + fid).toString();
        br.getHeaders().put("Upgrade-Insecure-Requests", "1");
        br.setRequest(null);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 1);
        if (downloadLink.getDownloadSize() > 0 && dl.getConnection().getLongContentLength() == 0) {
            dl.getConnection().disconnect();
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: Server sends empty file", 5 * 60 * 1000l);
        }
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (br.containsHTML("Accessing directly the download link doesn\\'t work")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 5 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
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

}