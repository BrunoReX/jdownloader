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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import jd.PluginWrapper;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.decrypter.GenericM3u8Decrypter.HlsContainer;

import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.downloader.hls.HLSDownloader;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "bbc.com" }, urls = { "http://bbcdecrypted/[pb][a-z0-9]{7}" }, flags = { 0 })
public class BbcCom extends PluginForHost {

    public BbcCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.bbc.co.uk/terms/";
    }

    private String rtmp_host     = null;
    private String rtmp_app      = null;
    private String rtmp_playpath = null;

    private String hls_master    = null;

    /** Thanks goes to: https://github.com/rg3/youtube-dl/blob/master/youtube_dl/extractor/bbc.py */
    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final String vpid = new Regex(link.getDownloadURL(), "bbcdecrypted/(.+)").getMatch(0);
        String title = link.getStringProperty("decrypterfilename");
        /* HLS - try that first as it will give us higher bitrates */
        this.br.getPage("http://open.live.bbc.co.uk/mediaselector/5/select/version/2.0/mediaset/iptv-all/vpid/" + vpid);
        /* RTMP */
        if (br.getHttpConnection().getResponseCode() == 404) {
            /* Fallback to rtmp is sometimes needed e.g. vpid: p01dvmbh */
            this.br.getPage("http://open.live.bbc.co.uk/mediaselector/5/select/version/2.0/mediaset/pc/vpid/" + vpid);
        }
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String title_downloadurl = null;
        long filesize_max = 0;
        long filesize_temp = 0;
        final String media[] = this.br.getRegex("<media(.*?)</media>").getColumn(0);
        for (final String mediasingle : media) {
            final String filesize_str = new Regex(mediasingle, "media_file_size=\"(\\d+)\"").getMatch(0);
            title_downloadurl = new Regex(mediasingle, "([^<>\"/]+)\\.mp4").getMatch(0);
            hls_master = new Regex(mediasingle, "\"(https?://[^<>\"]+\\.m3u8[^<>\"]*?)\"").getMatch(0);
            rtmp_app = new Regex(mediasingle, "application=\"([^<>\"]+)\"").getMatch(0);
            rtmp_host = new Regex(mediasingle, "server=\"([^<>\"]+)\"").getMatch(0);
            rtmp_playpath = new Regex(mediasingle, "identifier=\"((?:mp4|flv):[^<>\"]+)\"").getMatch(0);
            if (filesize_str == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            filesize_temp = Long.parseLong(filesize_str);
            if (filesize_temp > filesize_max) {
                filesize_max = filesize_temp;
            }
        }

        if (title == null) {
            title = title_downloadurl;
        }
        if (title == null) {
            /* Finally, fallback to vpid as filename */
            title = vpid;
        }

        link.setFinalFileName(title + ".mp4");
        link.setDownloadSize(filesize_max);

        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        if (hls_master != null) {
            br.getPage(hls_master);
            final HlsContainer hlsbest = jd.plugins.decrypter.GenericM3u8Decrypter.findBestVideoByBandwidth(jd.plugins.decrypter.GenericM3u8Decrypter.getHlsQualities(this.br));
            if (hlsbest == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final String url_hls = hlsbest.downloadurl;
            checkFFmpeg(downloadLink, "Download a HLS Stream");
            dl = new HLSDownloader(downloadLink, br, url_hls);
            dl.startDownload();
        } else {
            if (this.rtmp_app == null || this.rtmp_host == null || this.rtmp_playpath == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final String rtmpurl = "rtmp://" + this.rtmp_host;
            try {
                dl = new RTMPDownload(this, downloadLink, rtmpurl);
            } catch (final NoClassDefFoundError e) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "RTMPDownload class missing");
            }
            /* Setup rtmp connection */
            jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();
            rtmp.setUrl(rtmpurl);
            rtmp.setPlayPath(this.rtmp_playpath);
            rtmp.setApp(this.rtmp_app);
            rtmp.setFlashVer("WIN 19,0,0,245");
            rtmp.setResume(false);
            ((RTMPDownload) dl).startDownload();
        }
    }

    @SuppressWarnings({ "static-access" })
    private String formatDate(String input) {
        final long date;
        if (input.matches("\\d+")) {
            date = Long.parseLong(input) * 1000;
        } else {
            final Calendar cal = Calendar.getInstance();
            input += cal.get(cal.YEAR);
            date = TimeFormatter.getMilliSeconds(input, "E '|' dd.MM.yyyy", Locale.GERMAN);
        }
        String formattedDate = null;
        final String targetFormat = "yyyy-MM-dd";
        Date theDate = new Date(date);
        try {
            final SimpleDateFormat formatter = new SimpleDateFormat(targetFormat);
            formattedDate = formatter.format(theDate);
        } catch (Exception e) {
            /* prevent input error killing plugin */
            formattedDate = input;
        }
        return formattedDate;
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