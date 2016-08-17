//jDownloader - Downloadmanager
//Copyright (C) 2012  JD-Team support@jdownloader.org
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
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "soundowl.com" }, urls = { "http://(www\\.)?soundowl\\.com/track/[a-z0-9]+|http://dl\\.soundowl\\.com/[a-z0-9]+\\.mp3" }) 
public class SoundOwlCom extends PluginForHost {

    public SoundOwlCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String fuid = null;
    private String link = null;

    @Override
    public String getAGBLink() {
        return "http://soundowl.com/";
    }

    private String getFUID(final DownloadLink downloadLink) {
        return getFUID(downloadLink.getDownloadURL());
    }

    private String getFUID(final String link) {
        String fuid = new Regex(link, "\\.com/track/([^/]+)").getMatch(0);
        if (fuid == null) {
            fuid = new Regex(link, "dl\\.soundowl\\.com/([a-z0-9]+)\\.mp3").getMatch(0);
        }
        return fuid;
    }

    public void correctDownloadLink(final DownloadLink link) {
        if (link.getDownloadURL().contains("dl.")) {
            link.setUrlDownload("http://soundowl.com/track/" + getFUID(link));
        }
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.103 Safari/537.36");
        br.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        br.getHeaders().put("Accept-Charset", null);
        br.getHeaders().put("Accept-Language", "en-us,en;q=0.5");
        link = downloadLink.getDownloadURL();
        fuid = getFUID(downloadLink);
        br.getPage(link);
        // Found no offline links yet
        if (br.containsHTML(">This track has been removed")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<title>([^<>\"]+) (?:MP3\\s*)?download</title>").getMatch(0);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        downloadLink.setFinalFileName(Encoding.htmlDecode(filename.trim()) + ".mp3");
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        {
            Browser ajax = br.cloneBrowser();
            ajax.getHeaders().put("Accept", "*/*");
            ajax.getPage("/api/search/related/" + fuid);
        }
        br.setFollowRedirects(false);
        String url = "http://dl.soundowl.com/" + fuid + ".mp3";
        br.getPage(url);
        url = br.getRedirectLocation();
        if (url == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getHeaders().put("Referer", link);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, url, true, 0);
        if (dl.getConnection().getContentType().contains("html") && !dl.getConnection().isContentDisposition()) {
            br.followConnection();
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