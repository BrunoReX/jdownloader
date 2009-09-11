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

import java.io.IOException;

import jd.PluginWrapper;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.DownloadLink.AvailableStatus;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "partage-facile.com" }, urls = { "http://[\\w\\.]*?partage-facile\\.com/([0-9A-Z]+/.+|\\d+.*?\\.html)" }, flags = { 0 })
public class PartageFacileCom extends PluginForHost {

    public PartageFacileCom(PluginWrapper wrapper) {
        super(wrapper);
        br.setFollowRedirects(true);
    }

    public String getAGBLink() {
        return "http://www.partage-facile.com/cgu.php";
    }

    public AvailableStatus requestFileInformation(DownloadLink downloadLink) throws IOException, InterruptedException, PluginException {
        this.setBrowserExclusive();
        br.getPage(downloadLink.getDownloadURL());
        if (br.containsHTML("title\">Envoyez vos fichiers maintenant</td>")) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        Regex reg = br.getRegex("<br>Fichier : <b>(.*?)</b> \\((.*?)\\)<br>");
        String filesize = reg.getMatch(1);
        if (filesize == null) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        String filename = reg.getMatch(0);
        if (filesize.contains("Mo")) {
            filesize = filesize.replaceAll("Mo", "MB");
        } else if (filesize.contains("Go")) {
            filesize = filesize.replaceAll("Go", "GB");
        } else if (filesize.contains("Ko")) {
            filesize = filesize.replaceAll("Ko", "KB");
        } else if (filesize.contains("oct")) {
            filesize = filesize.replaceAll("oct", "b");
        } else {
            filesize = null;
        }
        if (filename == null || filesize == null) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        downloadLink.setName(filename.trim());
        downloadLink.setDownloadSize(Regex.getSize(filesize));
        return AvailableStatus.TRUE;
    }

    public void handleFree(DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        Form dlform0 = br.getForm(1);
        if (dlform0 == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFEKT);
        br.submitForm(dlform0);
        Form dlform1 = br.getForm(0);
        if (dlform1 == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFEKT);
        br.setFollowRedirects(true);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dlform1, false, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            if (br.containsHTML("Taille maximum du fichier")) throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, 30 * 1000l);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFEKT);
        }
        dl.startDownload();
    }

    public int getMaxSimultanFreeDownloadNum() {
        return getMaxSimultanDownloadNum();
    }

    public void reset() {
    }

    public void resetPluginGlobals() {
    }

    public void resetDownloadlink(DownloadLink link) {

    }
}
