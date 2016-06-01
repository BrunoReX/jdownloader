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

import jd.PluginWrapper;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "downloadandroidrom.com" }, urls = { "http://(www\\.)?downloadandroidrom\\.com/file/[^<>\"/]+/.+" }, flags = { 0 })
public class DownloadAndroidRomCom extends PluginForHost {

    public DownloadAndroidRomCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://downloadandroidrom.com/about.php";
    }

    private static final String NOCHUNKS = "NOCHUNKS";

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getDownloadURL());
        if (br.containsHTML(">The file you are looking for doesn\\'t exist")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        /* User added wrong link */
        if (!br.containsHTML("id=\"download_link\"")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<b>Name:</b>([^<>\"]*?)</p>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("\">([^<>\"]*?)</a><span style=\"font-size:0.9em;\">").getMatch(0);
        }
        String filesize = br.getRegex("File (?:s|S)ize:[\t\n\r ]*?</b>([^<>\"]*?)<").getMatch(0);
        if (filename == null || filesize == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(Encoding.htmlDecode(filename.trim()));
        link.setDownloadSize(SizeFormatter.getSize(filesize));
        final String md5 = br.getRegex("<b>MD5 Checksum:</b> ([a-z0-9]{32})</p>").getMatch(0);
        if (md5 != null) {
            link.setMD5Hash(md5);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        final Regex dllinkregex = br.getRegex("\"(http://[A-Za-z0-9]+\\.)(downloadandroidrom\\.com/download/[^<>\"]*?)\"");
        final String wwwpart = dllinkregex.getMatch(0);
        String dllink = dllinkregex.getMatch(1);
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (wwwpart.contains("www.")) {
            // this.sleep(5 * 1001l, downloadLink);
            br.postPage("http://downloadandroidrom.com/gettoken2.php", "");
            String token = br.toString();
            if (token.length() >= 50) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.postPage("http://downloadandroidrom.com/testbusy.php", "");
            final String server = br.toString();
            if (server.length() >= 50) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            token = Encoding.htmlDecode(token.trim());
            dllink = "http://mirror" + server + "." + dllink + token;
            br.getPage("http://downloadandroidrom.com/ip/ip.php");
        } else {
            dllink = wwwpart + dllink;
        }

        int maxchunks = 0;
        if (downloadLink.getBooleanProperty(NOCHUNKS, false)) {
            maxchunks = 1;
        }

        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        try {
            if (!this.dl.startDownload()) {
                try {
                    if (dl.externalDownloadStop()) {
                        return;
                    }
                } catch (final Throwable e) {
                }
                /* unknown error, we disable multiple chunks */
                if (downloadLink.getBooleanProperty(DownloadAndroidRomCom.NOCHUNKS, false) == false) {
                    downloadLink.setProperty(DownloadAndroidRomCom.NOCHUNKS, Boolean.valueOf(true));
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            }
        } catch (final PluginException e) {
            // New V2 errorhandling
            /* unknown error, we disable multiple chunks */
            if (e.getLinkStatus() != LinkStatus.ERROR_RETRY && downloadLink.getBooleanProperty(DownloadAndroidRomCom.NOCHUNKS, false) == false) {
                downloadLink.setProperty(DownloadAndroidRomCom.NOCHUNKS, Boolean.valueOf(true));
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            throw e;
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

}