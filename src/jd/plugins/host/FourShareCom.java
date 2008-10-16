//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
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

package jd.plugins.host;

import java.io.IOException;

import jd.PluginWrapper;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.download.RAFDownload;

public class FourShareCom extends PluginForHost {

    private static int COUNTER = 0;

    public FourShareCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.4shared.com/terms.jsp";
    }

    @Override
    public boolean getFileInformation(DownloadLink downloadLink) throws IOException {
        br.setCookiesExclusive(true);
        br.clearCookies(getHost());
        br.setFollowRedirects(true);
        br.getPage(downloadLink.getDownloadURL());
        String filename = br.getRegex("<title>4shared\\.com \\- online file sharing and storage \\- download(.*?)</title>").getMatch(0);
        String size = br.getRegex("<td style=.*?><b>Size:</b></td>.*?<td style=.*?>(.*?)</td>").getMatch(0);
        downloadLink.setName(filename.trim());
        downloadLink.setDownloadSize(Regex.getSize(size.replace(",", "")));
        return true;
    }

    @Override
    public String getVersion() {
        
        return getVersion("$Revision$");
    }

    public static synchronized void increaseCounter() {
        COUNTER++;
    }

    public static synchronized void decreaseCounter() {
        COUNTER--;
    }

    public void handleFree(DownloadLink downloadLink) throws Exception {
        try {

            handleFree0(downloadLink);
            decreaseCounter();
        } catch (Exception e) {
            decreaseCounter();
            throw e;
        }

    }

    public void handleFree0(DownloadLink downloadLink) throws Exception {
        if (!getFileInformation(downloadLink)) { throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND); }
        String url = br.getRegex("<a href=\"(http://www.4shared.com/get.*?)\" class=\"dbtn\" tabindex=\"1\">").getMatch(0);

        br.getPage(url);
        this.sleep(Integer.parseInt(br.getRegex(" var c = (\\d+?);").getMatch(0)) * 1000l, downloadLink);
        url = br.getRegex("id=\\'divDLStart\\' >.*?<a href=\\'(.*?)\'>Click here to download this file</a>.*?</div>").getMatch(0);
        downloadLink.getLinkStatus().setStatusText("Waiting...");
        downloadLink.requestGuiUpdate();
        // Das wartesystem lässt link b warten während link a lädt
        while (COUNTER > 0) {
            Thread.sleep(100);
        }
        increaseCounter();

        ;
        dl = RAFDownload.download(downloadLink, br.createGetRequest(url), false, 1);

        String error = new Regex(dl.connect().getURL(), "\\?error(.*)").getMatch(0);
        if (error != null) { throw new PluginException(LinkStatus.ERROR_RETRY, error); }

        dl.startDownload();
    }

    public int getMaxSimultanFreeDownloadNum() {
        return 2;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

}