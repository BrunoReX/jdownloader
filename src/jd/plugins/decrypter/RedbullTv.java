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

package jd.plugins.decrypter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.ProgressController;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "redbull.tv" }, urls = { "https?://(www\\.)?redbull.tv/(?:episodes|videos)/[A-Z0-9\\-]+/[a-z0-9\\-]+" }, flags = { 0 })
public class RedbullTv extends PluginForDecrypt {

    @SuppressWarnings("deprecation")
    public RedbullTv(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String DOMAIN         = "redbull.tv";
    /* Settings stuff */
    private static final String FAST_LINKCHECK = "FAST_LINKCHECK";

    /* Thx https://github.com/bromix/repository.bromix.storage/tree/master/plugin.video.redbull.tv */
    @SuppressWarnings({ "unchecked", "deprecation" })
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        /* Lost sister-host plugin */
        JDUtilities.getPluginForHost("redbull.tv");
        final String parameter = param.toString();
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final HashMap<String, String[]> formats = jd.plugins.hoster.RedbullTv.formats;
        final String nicehost = new Regex(parameter, "http://(?:www\\.)?([^/]+)").getMatch(0);
        final String decryptedhost = "http://" + nicehost + "decrypted";
        final SubConfiguration cfg = SubConfiguration.getConfig(DOMAIN);
        final boolean fastLinkcheck = cfg.getBooleanProperty(FAST_LINKCHECK, false);
        boolean httpAvailable = false;
        final String vid = new Regex(parameter, "redbull.tv/[^/]+/([A-Z0-9\\-]+)/").getMatch(0);
        // if (parameter.matches(type_unsupported)) {
        // try {
        // decryptedLinks.add(this.createOfflinelink(parameter));
        // } catch (final Throwable e) {
        // /* Not available in old 0.9.581 Stable */
        // }
        // return decryptedLinks;
        // }
        br.getPage("https://api.redbull.tv/v1/videos/" + vid);
        if (br.getHttpConnection().getResponseCode() == 404) {
            try {
                decryptedLinks.add(this.createOfflinelink(parameter));
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
            }
            return decryptedLinks;
        }
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
        final String title = (String) entries.get("title");
        final String subtitle = (String) entries.get("subtitle");
        final String description = (String) entries.get("long_description");
        String main_title = title;
        long season = -1;
        long episode = -1;
        if (entries.get("season_number") != null) {
            season = ((Number) entries.get("season_number")).longValue();
        }
        if (entries.get("episode_number") != null) {
            episode = ((Number) entries.get("episode_number")).longValue();
        }
        if (season > -1 && episode > -1) {
            final DecimalFormat df = new DecimalFormat("00");
            main_title += " - S" + df.format(season) + "E" + df.format(episode);
        }
        main_title = main_title + " - " + subtitle;
        entries = (LinkedHashMap<String, Object>) entries.get("videos");
        if (entries.get("master") != null) {
            httpAvailable = true;
            entries = (LinkedHashMap<String, Object>) entries.get("master");
        } else {
            entries = (LinkedHashMap<String, Object>) entries.get("demand");
        }
        if (entries == null) {
            /* Probably an ongoing/outstanding live stream --> Offline */
            try {
                decryptedLinks.add(this.createOfflinelink(parameter));
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
            }
            return decryptedLinks;
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(main_title);
        if (httpAvailable) {
            /* Typically available for all normal videos. */
            entries = (LinkedHashMap<String, Object>) entries.get("renditions");
            final Set<Entry<String, Object>> entryset = entries.entrySet();
            for (Entry<String, Object> entry : entryset) {
                final String bitrate = entry.getKey();
                final String finallink = (String) entry.getValue();

                if (formats.containsKey(bitrate) && cfg.getBooleanProperty(bitrate, false)) {
                    final DownloadLink dl = createDownloadlink(decryptedhost + System.currentTimeMillis() + new Random().nextInt(1000000000));
                    final String[] vidinfo = formats.get(bitrate);
                    String filename = title + "_" + getFormatString(vidinfo);
                    filename += ".mp4";

                    try {
                        dl.setContentUrl(parameter);
                        if (description != null) {
                            dl.setComment(description);
                        }
                        dl.setLinkID(vid + filename);
                    } catch (final Throwable e) {
                        /* Not available in old 0.9.581 Stable */
                    }
                    dl._setFilePackage(fp);
                    dl.setProperty("format", bitrate);
                    dl.setProperty("mainlink", parameter);
                    dl.setProperty("directlink", finallink);
                    dl.setProperty("directfilename", filename);
                    dl.setFinalFileName(filename);
                    if (fastLinkcheck) {
                        dl.setAvailable(true);
                    }
                    decryptedLinks.add(dl);
                }
            }
        } else {
            /* TODO: Add quality settings for this too in case there is demand. */
            /* Typically only needed for "live" streams (recordings) as for some reason they don't have http sources available. */
            final String hlsurl = (String) entries.get("uri");
            if (hlsurl == null) {
                return null;
            }
            decryptedLinks.add(createDownloadlink(hlsurl));
        }

        return decryptedLinks;
    }

    private String getFormatString(final String[] formatinfo) {
        String formatString = "";
        final String videoCodec = formatinfo[0];
        final String videoBitrate = formatinfo[1];
        final String videoResolution = formatinfo[2];
        final String audioCodec = formatinfo[3];
        final String audioBitrate = formatinfo[4];
        if (videoCodec != null) {
            formatString += videoCodec + "_";
        }
        if (videoResolution != null) {
            formatString += videoResolution + "_";
        }
        if (videoBitrate != null) {
            formatString += videoBitrate + "_";
        }
        if (audioCodec != null) {
            formatString += audioCodec + "_";
        }
        if (audioBitrate != null) {
            formatString += audioBitrate;
        }
        if (formatString.endsWith("_")) {
            formatString = formatString.substring(0, formatString.lastIndexOf("_"));
        }
        return formatString;
    }

}
