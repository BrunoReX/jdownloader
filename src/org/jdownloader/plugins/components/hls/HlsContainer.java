package org.jdownloader.plugins.components.hls;

import java.util.ArrayList;
import java.util.List;

import jd.http.Browser;

import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;

public class HlsContainer {

    public static HlsContainer findBestVideoByBandwidth(final List<HlsContainer> media) {
        if (media == null) {
            return null;
        }
        HlsContainer best = null;
        long bandwidth_highest = 0;
        for (final HlsContainer hls : media) {
            final long bandwidth_temp = hls.bandwidth;
            if (bandwidth_temp > bandwidth_highest) {
                bandwidth_highest = bandwidth_temp;
                best = hls;
            }
        }
        return best;
    }

    public static List<HlsContainer> getHlsQualities(final Browser br) throws Exception {
        final ArrayList<HlsContainer> hlsqualities = new ArrayList<HlsContainer>();
        final String[][] streams = br.getRegex("#EXT-X-STREAM-INF:?([^\r\n]+)[\r\n]+([^\r\n]+)").getMatches();
        if (streams == null) {
            return null;
        }
        for (final String stream[] : streams) {
            if (StringUtils.isNotEmpty(stream[1])) {
                final String streamInfo = stream[0];
                // name = quality
                // final String quality = new Regex(media, "NAME=\"(.*?)\"").getMatch(0);
                final String bandwidth = new Regex(streamInfo, "BANDWIDTH=(\\d+)").getMatch(0);
                final String resolution = new Regex(streamInfo, "RESOLUTION=(\\d+x\\d+)").getMatch(0);
                final String codecs = new Regex(streamInfo, "CODECS=\"([^<>\"]+)\"").getMatch(0);
                final String url = br.getURL(stream[1]).toString();
                final HlsContainer hls = new HlsContainer();
                if (bandwidth != null) {
                    hls.bandwidth = Long.parseLong(bandwidth);
                } else {
                    hls.bandwidth = -1;
                }
                if (codecs != null) {
                    hls.codecs = codecs.trim();
                }
                hls.downloadurl = url;
                if (resolution != null) {
                    final String[] resolution_info = resolution.split("x");
                    final String width = resolution_info[0];
                    final String height = resolution_info[1];
                    hls.width = Integer.parseInt(width);
                    hls.height = Integer.parseInt(height);
                }
                hlsqualities.add(hls);
            }
        }
        return hlsqualities;
    }

    public String codecs;
    public String downloadurl;

    public String getDownloadurl() {
        return downloadurl;
    }

    public int  width     = -1;
    public int  height    = -1;
    public long bandwidth = -1;

    public String getResolution() {
        return width + "x" + height;
    }

    public HlsContainer() {
    }

    @Override
    public String toString() {
        return getStandardFilename();
    }

    public String getStandardFilename() {
        String filename = "";
        if (width != -1 && height != -1) {
            filename += getResolution();
        }
        if (codecs != null) {
            filename += "_" + codecs;
        }
        if (StringUtils.equalsIgnoreCase(codecs, "mp4a.40.34")) {
            filename += ".mp3";
        } else if (StringUtils.equalsIgnoreCase(codecs, "mp4a.40.5") || StringUtils.equalsIgnoreCase(codecs, "mp4a.40.2")) {
            filename += ".aac";
        } else {
            filename += ".mp4";
        }
        return filename;
    }

}