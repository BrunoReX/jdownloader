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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.scripting.JavaScriptEngineFactory;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "rai.tv" }, urls = { "https?://[A-Za-z0-9\\.]*?(?:rai\\.tv|raiyoyo\\.rai\\.it)/.+\\?day=\\d{4}\\-\\d{2}\\-\\d{2}.*|https?://[A-Za-z0-9\\.]*?(?:rai\\.tv|rai\\.it|raiplay\\.it)/.+\\.html" })
public class RaiItDecrypter extends PluginForDecrypt {

    public RaiItDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String     TYPE_DAY         = ".+\\?day=\\d{4}\\-\\d{2}\\-\\d{2}.*";
    private static final String     TYPE_CONTENTITEM = ".+/dl/[^<>\"]+/ContentItem\\-[a-f0-9\\-]+\\.html$";

    private static final String     TYPE_RAIPLAY_IT  = "https?://.+raiplay\\.it/.+";

    private ArrayList<DownloadLink> decryptedLinks   = new ArrayList<DownloadLink>();
    private String                  parameter        = null;

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        this.br.setFollowRedirects(true);
        parameter = param.toString();

        if (parameter.matches(TYPE_DAY)) {
            decryptWholeDay();
        } else {
            decryptSingleVideo();
        }

        return decryptedLinks;
    }

    private void decryptWholeDay() throws Exception {
        final String mainlink_urlpart = new Regex(parameter, "\\?(.+)").getMatch(0);
        final String[] dates = new Regex(parameter, "(\\d{4}\\-\\d{2}\\-\\d{2})").getColumn(0);
        final String[] channels = new Regex(parameter, "ch=(\\d+)").getColumn(0);
        final String[] videoids = new Regex(parameter, "v=(\\d+)").getColumn(0);
        final String date = dates[dates.length - 1];
        final String date_underscore = date.replace("-", "_");

        final DownloadLink offline = this.createOfflinelink(parameter);
        final String filename_offline;
        if (mainlink_urlpart != null) {
            filename_offline = date + "_" + mainlink_urlpart + ".mp4";
        } else {
            filename_offline = date + ".mp4";
        }
        offline.setFinalFileName(filename_offline);

        try {
            String id_of_single_video_which_user_wants_to_have_only = null;
            String chnumber_str = null;
            if (channels != null && channels.length > 0) {
                chnumber_str = channels[channels.length - 1];
            }
            if (chnumber_str == null) {
                /* Small fallback */
                chnumber_str = "1";
            }
            if (videoids != null && videoids.length > 0) {
                id_of_single_video_which_user_wants_to_have_only = videoids[videoids.length - 1];
            }
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(date_underscore);
            // fp.setProperty("ALLOW_MERGE", true);
            LinkedHashMap<String, Object> tempmap = null;
            LinkedHashMap<String, Object> entries = null;
            ArrayList<Object> ressourcelist = null;
            /* Find the name of our channel needed for the following requests */
            this.br.getPage("http://www.rai.tv/dl/RaiTV/iphone/android/smartphone/advertising_config.html");
            String channel_name = null;
            entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(br.toString());
            ressourcelist = (ArrayList<Object>) entries.get("Channels");
            for (final Object channelo : ressourcelist) {
                entries = (LinkedHashMap<String, Object>) channelo;
                final String channelnumber = (String) entries.get("id");
                if (channelnumber.equals(chnumber_str)) {
                    channel_name = (String) entries.get("tag");
                    break;
                }
            }
            if (channel_name == null || channel_name.equals("")) {
                channel_name = "RaiUno";
            }

            this.br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            this.br.getPage("http://www.rai.tv/dl/portale/html/palinsesti/replaytv/static/" + channel_name + "_" + date_underscore + ".html");
            if (br.getHttpConnection().getResponseCode() == 404) {
                decryptedLinks.add(offline);
                return;
            }
            entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(br.toString());
            entries = (LinkedHashMap<String, Object>) entries.get(chnumber_str);
            entries = (LinkedHashMap<String, Object>) entries.get(date);

            final Iterator<Entry<String, Object>> it = entries.entrySet().iterator();
            while (it.hasNext()) {
                if (this.isAbort()) {
                    logger.info("Decryption aborted by user");
                    return;
                }
                final Entry<String, Object> entry = it.next();
                tempmap = (LinkedHashMap<String, Object>) entry.getValue();
                String title = (String) tempmap.get("t");
                final String videoid_temp = (String) tempmap.get("i");
                final String relinker = (String) tempmap.get("r");
                final String description = (String) tempmap.get("d");
                if (title == null || title.equals("") || relinker == null || relinker.equals("")) {
                    if (id_of_single_video_which_user_wants_to_have_only != null && videoid_temp != null && videoid_temp.equals(id_of_single_video_which_user_wants_to_have_only)) {
                        logger.info("User wants to have a single video only but this appears to be offline");
                        offline.setFinalFileName(id_of_single_video_which_user_wants_to_have_only + ".mp4");
                        /* Remove previously found content */
                        decryptedLinks.clear();
                        /* Add offline */
                        decryptedLinks.add(offline);
                        return;
                    }
                    continue;
                }
                title = date_underscore + "_raitv_" + title;
                if (id_of_single_video_which_user_wants_to_have_only != null && videoid_temp != null && videoid_temp.equals(id_of_single_video_which_user_wants_to_have_only)) {
                    /* User wants to have a specified video only and we found it. */
                    decryptRelinker(relinker, title, null, fp, description);
                    return;
                } else if (id_of_single_video_which_user_wants_to_have_only != null && videoid_temp != null && !videoid_temp.equals(id_of_single_video_which_user_wants_to_have_only)) {
                    /* User wants to have a specified video only but this is not the one he wants --> Skip this */
                    continue;
                } else {
                    /* Simply add video */
                    decryptRelinker(relinker, title, null, fp, description);
                }
            }
        } finally {
            if (decryptedLinks.size() == 0) {
                /* Probably none of the urls was downloadable ... */
                decryptedLinks.add(offline);
            }
        }
    }

    private void decryptSingleVideo() throws DecrypterException, Exception {
        String dllink = null;
        String title = null;
        String extension = ".mp4";
        String date = null;
        String date_formatted = null;
        String description = null;

        this.br.getPage(this.parameter);
        final String jsredirect = this.br.getRegex("document\\.location\\.replace\\(\\'(http[^<>\"]*?)\\'\\)").getMatch(0);
        if (jsredirect != null) {
            this.br.getPage(jsredirect.trim());
        }
        if (this.br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(this.parameter));
            return;
        }
        /* Do NOT use value of "videoURL_MP4" here! */
        /* E.g. http://www.rai.tv/dl/RaiTV/programmi/media/ContentItem-70996227-7fec-4be9-bc49-ba0a8104305a.html */
        dllink = this.br.getRegex("var[\t\n\r ]*?videoURL[\t\n\r ]*?=[\t\n\r ]*?\"(http://[^<>\"]+)\"").getMatch(0);
        String content_id_from_url = null;
        if (this.parameter.matches(TYPE_CONTENTITEM)) {
            content_id_from_url = new Regex(this.parameter, "(\\-[a-f0-9\\-]+)\\.html$").getMatch(0);
        }
        if (dllink == null) {
            dllink = findRelinkerUrl();
        }
        title = this.br.getRegex("property=\"og:title\" content=\"([^<>\"]+)\"").getMatch(0);
        date = this.br.getRegex("content=\"(\\d{4}\\-\\d{2}\\-\\d{2}) \\d{2}:\\d{2}:\\d{2}\" property=\"gen\\-date\"").getMatch(0);

        final String contentset_id = this.br.getRegex("var[\t\n\r ]*?urlTop[\t\n\r ]*?=[\t\n\r ]*?\"[^<>\"]+/ContentSet([A-Za-z0-9\\-]+)\\.html").getMatch(0);
        final String content_id_from_html = this.br.getRegex("id=\"ContentItem(\\-[a-f0-9\\-]+)\"").getMatch(0);
        if (br.getHttpConnection().getResponseCode() == 404 || (contentset_id == null && content_id_from_html == null && dllink == null)) {
            /* Probably not a video/offline */
            decryptedLinks.add(this.createOfflinelink(this.parameter));
            return;
        }
        if (dllink != null) {
            if (title == null) {
                /* Streamurls directly in html */
                title = this.br.getRegex("id=\"idMedia\">([^<>]+)<").getMatch(0);
            }
            if (title == null) {
                title = this.br.getRegex("var videoTitolo\\d*?=\\d*?\"([^<>\"]+)\";").getMatch(0);
            }
            if (date == null) {
                date = this.br.getRegex("id=\"myGenDate\">(\\d{2}\\-\\d{2}\\-\\d{4} \\d{2}:\\d{2})<").getMatch(0);
            }
            if (date == null) {
                date = this.br.getRegex("data\\-date=\"(\\d{2}/\\d{2}/\\d{4})\"").getMatch(0);
            }
        } else {
            LinkedHashMap<String, Object> entries = null;
            if (content_id_from_html != null) {
                /* Easiest way to find videoinfo */
                this.br.getPage("http://www.rai.tv/dl/RaiTV/programmi/media/ContentItem" + content_id_from_html + ".html?json");
                entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(br.toString());
            }
            if (entries == null) {
                final ArrayList<Object> ressourcelist;
                final String list_json_from_html = this.br.getRegex("\"list\"[\t\n\r ]*?:[\t\n\r ]*?(\\[.*?\\}[\t\n\r ]*?\\])").getMatch(0);
                if (list_json_from_html != null) {
                    ressourcelist = (ArrayList<Object>) JavaScriptEngineFactory.jsonToJavaObject(list_json_from_html);
                } else {
                    br.getPage("http://www.rai.tv/dl/RaiTV/ondemand/ContentSet" + contentset_id + ".html?json");
                    if (br.getHttpConnection().getResponseCode() == 404) {
                        decryptedLinks.add(this.createOfflinelink(this.parameter));
                        return;
                    }
                    entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(br.toString());
                    ressourcelist = (ArrayList<Object>) entries.get("list");
                }

                if (content_id_from_url == null) {
                    /* Hm probably not a video */
                    decryptedLinks.add(this.createOfflinelink(this.parameter));
                    return;
                }
                String content_id_temp = null;
                boolean foundVideoInfo = false;
                for (final Object videoo : ressourcelist) {
                    entries = (LinkedHashMap<String, Object>) videoo;
                    content_id_temp = (String) entries.get("itemId");
                    if (content_id_temp != null && content_id_temp.contains(content_id_from_url)) {
                        foundVideoInfo = true;
                        break;
                    }
                }
                if (!foundVideoInfo) {
                    /* Probably offline ... */
                    decryptedLinks.add(this.createOfflinelink(this.parameter));
                    return;
                }
            }
            date = (String) entries.get("date");
            title = (String) entries.get("name");
            description = (String) entries.get("desc");
            final String type = (String) entries.get("type");
            if (type.equalsIgnoreCase("RaiTv Media Video Item")) {
            } else {
                /* TODO */
                logger.warning("Unsupported media type!");
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
            extension = "mp4";
            dllink = (String) entries.get("h264");
            if (dllink == null || dllink.equals("")) {
                dllink = (String) entries.get("m3u8");
                extension = "mp4";
            }
            if (dllink == null || dllink.equals("")) {
                dllink = (String) entries.get("wmv");
                extension = "wmv";
            }
            if (dllink == null || dllink.equals("")) {
                dllink = (String) entries.get("mediaUri");
                extension = "mp4";
            }
        }
        if (title == null) {
            title = content_id_from_url;
        }
        date_formatted = jd.plugins.hoster.RaiTv.formatDate(date);
        title = Encoding.htmlDecode(title);
        title = date_formatted + "_raitv_" + title;
        title = encodeUnicode(title);

        final FilePackage fp = FilePackage.getInstance();
        fp.setName(title);

        decryptRelinker(dllink, title, extension, fp, description);
    }

    private void decryptRelinker(final String relinker_url, String title, String extension, final FilePackage fp, final String description) throws Exception {
        String dllink = relinker_url;
        if (extension != null && extension.equalsIgnoreCase("wmv")) {
            /* E.g. http://www.tg1.rai.it/dl/tg1/2010/rubriche/ContentItem-9b79c397-b248-4c03-a297-68b4b666e0a5.html */
            logger.info("Download http .wmv video");
        } else {
            final String cont = jd.plugins.hoster.RaiTv.getContFromRelinkerUrl(relinker_url);
            if (cont == null) {
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
            /* Drop previous Headers & Cookies */
            this.br = jd.plugins.hoster.RaiTv.prepVideoBrowser(new Browser());
            jd.plugins.hoster.RaiTv.accessCont(this.br, cont);

            if (this.br.containsHTML("video_no_available\\.mp4")) {
                /* Offline/Geo-Blocked */
                /* XML response with e.g. this (and some more): <url>http://download.rai.it/video_no_available.mp4</url> */
                final DownloadLink offline = this.createOfflinelink(relinker_url);
                if (title == null) {
                    title = cont;
                }
                offline.setName("GEOBLOCKED_" + title);
                this.decryptedLinks.add(offline);
                return;
            }

            dllink = jd.plugins.hoster.RaiTv.getDllink(this.br);
            if (dllink == null) {
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
            if (extension == null && dllink.contains(".mp4")) {
                extension = "mp4";
            } else if (extension == null && dllink.contains(".wmv")) {
                extension = "wmv";
            } else if (extension == null) {
                /* Final fallback */
                extension = "mp4";
            }
            if (!jd.plugins.hoster.RaiTv.dllinkIsDownloadable(dllink)) {
                logger.info("Unsupported streaming protocol");
                throw new DecrypterException(DecrypterException.PLUGIN_DEFECT);
            }
        }
        final Regex hdsconvert = new Regex(dllink, "(https?://[^/]+/z/podcastcdn/.+\\.csmil)/manifest\\.f4m");
        if (hdsconvert.matches()) {
            /* Convert hds --> hls */
            dllink = hdsconvert.getMatch(0).replace("/z/", "/i/") + "/index_1_av.m3u8";
        }
        if (dllink.contains(".m3u8")) {
            this.br.getPage(dllink);
            final List<HlsContainer> allqualities = HlsContainer.getHlsQualities(this.br);
            for (final HlsContainer singleHlsQuality : allqualities) {
                final DownloadLink dl = this.createDownloadlink(singleHlsQuality.downloadurl);
                final String filename = title + "_" + singleHlsQuality.getStandardFilename();
                dl.setFinalFileName(filename);
                dl._setFilePackage(fp);
                if (description != null) {
                    dl.setComment(description);
                }
                decryptedLinks.add(dl);
            }
        } else {
            /* Single http url --> We can sometimes grab multiple qualities */
            if (dllink.contains("_1800.mp4")) {
                /* Multiple qualities availab.e */
                final String[][] bitrates = { { "1800", "_1800.mp4" }, { "800", "_800.mp4" } };
                for (final String[] qualityInfo : bitrates) {
                    final String bitrate = qualityInfo[0];
                    final String url_bitrate_string = qualityInfo[1];
                    final String directlink = dllink.replace("_1800.mp4", url_bitrate_string);
                    final DownloadLink dl = this.createDownloadlink("directhttp://" + directlink);
                    dl.setFinalFileName(title + "_" + bitrate + "." + extension);
                    dl._setFilePackage(fp);
                    if (description != null) {
                        dl.setComment(description);
                    }
                    this.decryptedLinks.add(dl);
                }
            } else {
                /* Only one quality available. */
                final DownloadLink dl = this.createDownloadlink("directhttp://" + dllink);
                dl.setFinalFileName(title + "." + extension);
                dl._setFilePackage(fp);
                if (description != null) {
                    dl.setComment(description);
                }
                this.decryptedLinks.add(dl);
            }
        }
    }

    private String findRelinkerUrl() {
        return this.br.getRegex("(https?://mediapolisvod\\.rai\\.it/relinker/relinkerServlet\\.htm\\?cont=[A-Za-z0-9]+)").getMatch(0);
    }

}
