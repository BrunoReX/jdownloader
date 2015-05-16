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

package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.utils.JDUtilities;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

// http://tvthek,orf.at/live/... --> HDS
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "orfmediathek.at" }, urls = { "http://(www\\.)?tvthek\\.orf\\.at/(programs?|topic)/.+" }, flags = { 0 })
public class ORFMediathekDecrypter extends PluginForDecrypt {

    private static final String Q_SUBTITLES   = "Q_SUBTITLES";
    private static final String Q_BEST        = "Q_BEST_2";
    private static final String Q_LOW         = "Q_LOW";
    private static final String Q_MEDIUM      = "Q_MEDIUM";
    private static final String Q_HIGH        = "Q_HIGH";
    private static final String HTTP_STREAM   = "HTTP_STREAM";
    private boolean             BEST          = false;

    private static final String TYPE_TOPIC    = "http://(www\\.)?tvthek\\.orf\\.at/topic/.+";
    private static final String TYPE_PROGRAMM = "http://(www\\.)?tvthek\\.orf\\.at/programs?/.+";

    public ORFMediathekDecrypter(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        if (isStableEnviroment()) {
            return decryptedLinks;
        }
        String parameter = param.toString();

        try {
            br.getPage(parameter);
        } catch (final BrowserException e) {
            if (br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 500) {
                final DownloadLink link = createDownloadlink(parameter.replace("http://", "decrypted://") + "&quality=default&hash=default");
                link.setAvailable(false);
                link.setProperty("offline", true);
                decryptedLinks.add(link);
                return decryptedLinks;
            }
            throw e;
        }
        int status = br.getHttpConnection().getResponseCode();
        if (status == 301 || status == 302) {
            br.setFollowRedirects(true);
            if (br.getRedirectLocation() != null) {
                parameter = br.getRedirectLocation();
                br.getPage(parameter);
            }
        } else if (status != 200) {
            final DownloadLink link = createDownloadlink(parameter.replace("http://", "decrypted://") + "&quality=default&hash=default");
            link.setAvailable(false);
            link.setProperty("offline", true);
            decryptedLinks.add(link);
            return decryptedLinks;
        }
        if (br.containsHTML("(404 \\- Seite nicht gefunden\\.|area_headline error_message\">Keine Sendung vorhanden<)") || !br.containsHTML("class=\"video_headline\"")) {
            final DownloadLink link = createDownloadlink(parameter.replace("http://", "decrypted://") + "&quality=default&hash=default");
            link.setAvailable(false);
            link.setProperty("offline", true);
            link.setName(new Regex(parameter, "tvthek\\.orf\\.at/programs/(.+)").getMatch(0));
            decryptedLinks.add(link);
            return decryptedLinks;
        }

        final SubConfiguration cfg = SubConfiguration.getConfig("orf.at");
        BEST = cfg.getBooleanProperty(Q_BEST, false);
        decryptedLinks.addAll(getDownloadLinks(parameter, cfg));

        if (decryptedLinks == null || decryptedLinks.size() == 0) {
            if (parameter.matches(TYPE_TOPIC)) {
                logger.warning("MAYBE Decrypter out of date for link: " + parameter);
            } else {
                logger.warning("Decrypter out of date for link: " + parameter);
            }
            return null;
        }
        return decryptedLinks;
    }

    private boolean isStableEnviroment() {
        String prev = JDUtilities.getRevision();
        if (prev == null || prev.length() < 3) {
            prev = "0";
        } else {
            prev = prev.replaceAll(",|\\.", "");
        }
        final int rev = Integer.parseInt(prev);
        if (rev < 10000) {
            return true;
        }
        return false;
    }

    @SuppressWarnings({ "deprecation", "unchecked", "unused", "rawtypes" })
    private ArrayList<DownloadLink> getDownloadLinks(final String data, final SubConfiguration cfg) {
        ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String nicehost = new Regex(data, "http://(?:www\\.)?([^/]+)").getMatch(0);
        final String decryptedhost = "http://" + nicehost + "decrypted";

        try {
            final String json = br.getRegex("initializeAdworx\\((.*?)\\);\n").getMatch(0);
            final String video_id = new Regex(data, "(\\d+)$").getMatch(0);
            String xmlData = br.getRegex("ORF\\.flashXML\\s*=\\s*\'([^\']+)\';").getMatch(0);

            if (xmlData != null || json != null) {
                Map<String, HashMap<String, String>> mediaEntries = new TreeMap<String, HashMap<String, String>>();
                HashMap<String, String> mediaEntry = null;
                String quality = null, key = null, title = null;

                if (xmlData != null) {
                    /* TODO: Check if this is still needed and fix- or remove it */
                    Document doc = JDUtilities.parseXmlString(Encoding.htmlDecode(xmlData), false);

                    /* xmlData --> HashMap */
                    // /PlayerConfig/PlayList/Items/Item... --> name, quality, rtmp stream url
                    NodeList nl = doc.getElementsByTagName("Item");

                    for (int i = 0; i < nl.getLength(); i++) {
                        Node childNode = nl.item(i);
                        NodeList t = childNode.getChildNodes();
                        mediaEntry = new HashMap<String, String>();
                        for (int j = 0; j < t.getLength(); j++) {
                            Node g = t.item(j);
                            if ("#text".equals(g.getNodeName())) {
                                continue;
                            }
                            quality = ((Element) g).getAttribute("quality");
                            key = g.getNodeName();
                            if (isEmpty(quality) && "VideoUrl".equalsIgnoreCase(key)) {
                                continue;
                            }
                            if ("VideoUrl".equalsIgnoreCase(key)) {
                                key = quality;
                            }
                            mediaEntry.put(key, g.getTextContent());
                        }
                        title = mediaEntry.get("Title");
                        if (isEmpty(title)) {
                            continue;
                        }
                        mediaEntries.put(title, mediaEntry);
                    }
                } else {
                    /* jsonData --> HashMap */
                    mediaEntry = new HashMap<String, String>();
                    HashMap<String, String> tmpMediaEntry = new HashMap<String, String>();
                    ArrayList<Object> ressourcelist = (ArrayList) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(json);
                    LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) ressourcelist.get(ressourcelist.size() - 1);
                    entries = (LinkedHashMap<String, Object>) entries.get("values");
                    ressourcelist = (ArrayList) entries.get("segments");

                    for (final Object segmento : ressourcelist) {
                        final LinkedHashMap<String, Object> entry = (LinkedHashMap<String, Object>) segmento;
                        final LinkedHashMap<String, Object> playlist_data = (LinkedHashMap<String, Object>) entry.get("playlist_data");
                        final ArrayList<Object> sources = (ArrayList) playlist_data.get("sources");

                        final String encrypted_id = (String) entry.get("encrypted_id");
                        final String decrypted_id = Encoding.Base64Decode(encrypted_id);
                        final String description = (String) entry.get("description");
                        String titlethis = (String) entry.get("title");
                        if (titlethis == null) {
                            titlethis = description;
                        }
                        if (titlethis != null && titlethis.length() > 80) {
                            titlethis = titlethis.substring(0, 80);
                        }

                        for (final Object sourceo : sources) {
                            mediaEntry = new HashMap<String, String>();
                            final LinkedHashMap<String, Object> entry_source = (LinkedHashMap<String, Object>) sourceo;
                            final Iterator<Entry<String, Object>> it = entry_source.entrySet().iterator();
                            while (it.hasNext()) {
                                final Entry<String, Object> entry_entry = it.next();
                                final String ky = entry_entry.getKey();
                                if (entry_entry.getValue() instanceof String) {
                                    try {
                                        final String value = (String) entry_entry.getValue();
                                        mediaEntry.put(ky, value);
                                    } catch (final Throwable e) {
                                    }
                                }
                            }

                            /* Backward compatibility with xml method */
                            String url = (String) entry_source.get("src");
                            String fmt = (String) entry_source.get("quality");
                            String p = (String) entry_source.get("protocol");
                            String d = (String) entry_source.get("delivery");
                            if (isEmpty(url) && isEmpty(fmt) && isEmpty(p) && isEmpty(d)) {
                                continue;
                            }
                            String subtitle = new Regex("still_broken", "\"subtitles\":\\[\\{\"src\":\"(http[^\"]+)\"").getMatch(0);
                            if (subtitle != null) {
                                mediaEntry.put("SubTitleUrl", subtitle.replace("\\", ""));
                            }
                            mediaEntry.put("decrypted_id", decrypted_id);
                            String selector = p + d;
                            mediaEntry.put(fmt, url);
                            mediaEntry.put("selector", selector);

                            String entryKey = titlethis + "@" + selector;
                            if (video_id != null) {
                                entryKey += "_" + video_id;
                            }
                            if (decrypted_id != null) {
                                entryKey += "_" + decrypted_id;
                            }
                            entryKey += "@" + humanReadableQualityIdentifier(fmt.toUpperCase(Locale.ENGLISH).trim());
                            mediaEntries.put(entryKey, mediaEntry);
                        }
                    }
                }
                String fpName = getTitle(br);
                if (video_id != null) {
                    fpName += "_" + video_id;
                }
                String extension = ".mp4";
                if (br.getRegex("new MediaCollection\\(\"audio\",").matches()) {
                    extension = ".mp3";
                }

                ArrayList<DownloadLink> newRet = new ArrayList<DownloadLink>();
                ArrayList<DownloadLink> part = new ArrayList<DownloadLink>();
                HashMap<String, DownloadLink> bestMap = new HashMap<String, DownloadLink>();
                String vIdTemp = "";

                for (Entry<String, HashMap<String, String>> next : mediaEntries.entrySet()) {
                    mediaEntry = new HashMap<String, String>(next.getValue());
                    String fileName = next.getKey();
                    fileName = fileName.replaceAll("\"", "");
                    fileName = fileName.replaceAll(":\\s|\\s\\|\\s", " - ").trim();
                    final String video_id_detailed = mediaEntry.get("decrypted_id");
                    final String protocol = mediaEntry.get("protocol");
                    final String delivery = mediaEntry.get("delivery");
                    final String selector = mediaEntry.get("selector");
                    final String url = mediaEntry.get("src");
                    String fmt = mediaEntry.get("quality");

                    // available protocols: http, rtmp, rtsp, hds, hls
                    if (!"http".equals(protocol) || !"progressive".equals(delivery)) {
                        continue;
                    }
                    /* Leave this in in case we want to support rtmp versions again in the future. */
                    // if (cfg.getBooleanProperty(HTTP_STREAM, false) && "rtmp".equals(protocol)) {
                    // continue;
                    // }

                    if (fileName == null) {
                        continue;
                    }

                    boolean sub = true;
                    if (fileName.equals(vIdTemp)) {
                        sub = false;
                    }
                    if (!isEmpty(fmt)) {
                        fmt = humanReadableQualityIdentifier(fmt.toUpperCase(Locale.ENGLISH).trim());
                        /* best selection is done at the end */
                        if ("LOW".equals(fmt)) {
                            if ((cfg.getBooleanProperty(Q_LOW, true) || BEST) == false) {
                                continue;
                            } else {
                                fmt = "LOW";
                            }
                        } else if ("MEDIUM".equals(fmt)) {
                            if ((cfg.getBooleanProperty(Q_MEDIUM, true) || BEST) == false) {
                                continue;
                            } else {
                                fmt = "MEDIUM";
                            }
                        } else if ("HIGH".equals(fmt)) {
                            if ((cfg.getBooleanProperty(Q_HIGH, true) || BEST) == false) {
                                continue;
                            } else {
                                fmt = "HIGH";
                            }
                        } else {
                            if (unknownQualityIdentifier(fmt)) {
                                logger.info("ORFMediathek Decrypter: unknown quality identifier --> " + fmt);
                                logger.info("Link: " + data);
                            }
                            continue;
                        }
                    }
                    String ext = url.substring(url.lastIndexOf("."));
                    if (ext.length() == 4) {
                        extension = ext;
                    }
                    String name = fileName + (protocol != null ? "_" + protocol : "");
                    name += extension;
                    final DownloadLink link = createDownloadlink(decryptedhost + System.currentTimeMillis() + new Random().nextInt(1000000000));

                    link.setFinalFileName(name);
                    link.setContentUrl(data);
                    link.setProperty("directURL", url);
                    link.setProperty("directName", name);
                    link.setProperty("directQuality", fmt);
                    link.setProperty("mainlink", data);
                    if (protocol == null && delivery == null) {
                        link.setAvailable(true);
                        link.setProperty("streamingType", "rtmp");
                    } else {
                        link.setProperty("streamingType", protocol);
                        link.setProperty("delivery", delivery);
                        if (!"http".equals(protocol)) {
                            link.setAvailable(true);
                        }
                    }
                    link.setLinkID(video_id_detailed + "_" + fmt);

                    DownloadLink best = bestMap.get(fmt);
                    if (best == null || link.getDownloadSize() > best.getDownloadSize()) {
                        bestMap.put(fmt, link);
                    }
                    part.add(link);
                    if (sub) {
                        if (cfg.getBooleanProperty(Q_SUBTITLES, false)) {
                            String subtitleUrl = mediaEntry.get("SubTitleUrl");
                            if (!isEmpty(subtitleUrl)) {
                                final String name_subtitle = fileName + ".srt";
                                final DownloadLink subtitle = createDownloadlink(decryptedhost + System.currentTimeMillis() + new Random().nextInt(1000000000));
                                subtitle.setAvailable(true);
                                subtitle.setFinalFileName(name_subtitle);
                                subtitle.setContentUrl(data);
                                subtitle.setProperty("directURL", subtitleUrl);
                                subtitle.setProperty("directName", name_subtitle);
                                subtitle.setProperty("streamingType", "subtitle");
                                subtitle.setProperty("mainlink", data);
                                part.add(subtitle);
                                vIdTemp = fileName;
                            }
                        }
                    }
                    newRet.addAll(part);
                    part.clear();
                    bestMap.clear();
                }
                if (newRet.size() > 1) {
                    final FilePackage fp = FilePackage.getInstance();
                    fp.setName(fpName);
                    fp.addLinks(newRet);
                }
                ret = newRet;
            }
        } catch (final Throwable e) {
            e.printStackTrace();
            logger.severe(e.getMessage());
        }
        for (DownloadLink dl : ret) {
            try {
                distribute(dl);
            } catch (final Throwable e) {
                /* does not exist in 09581 */
            }
        }
        return ret;
    }

    private boolean isEmpty(String ip) {
        return ip == null || ip.trim().length() == 0;
    }

    private String getTitle(Browser br) {
        String title = br.getRegex("<title>(.*?)\\s*\\-\\s*ORF TVthek</title>").getMatch(0);
        String titleUT = br.getRegex("<span class=\"BoxHeadlineUT\">([^<]+)</").getMatch(0);
        if (title == null) {
            title = br.getRegex("<meta property=\"og:title\" content=\"([^\"]+)\"").getMatch(0);
        }
        if (title == null) {
            title = br.getRegex("\'playerTitle\':\\s*\'([^\'])\'$").getMatch(0);
        }
        if (title != null) {
            title = Encoding.htmlDecode(title + (titleUT != null ? "__" + titleUT.replaceAll(":$", "") : "").trim());
        }
        if (title == null) {
            title = "UnknownTitle_" + System.currentTimeMillis();
        }
        return title;
    }

    private String humanReadableQualityIdentifier(String s) {
        if ("Q1A".equals(s)) {
            return "LOW";
        }
        if ("Q4A".equals(s)) {
            return "MEDIUM";
        }
        if ("Q6A".equals(s)) {
            return "HIGH";
        }
        return s;
    }

    private boolean unknownQualityIdentifier(String s) {
        if (s.matches("(DESCRIPTION|SMIL|SUBTITLEURL|DURATION|TRANSCRIPTURL|TITLE|QUALITY|QUALITY_STRING|PROTOCOL|TYPE|DELIVERY)")) {
            return false;
        }
        return true;
    }

    private String decodeUnicode(String s) {
        Pattern p = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        String res = s;
        Matcher m = p.matcher(res);
        while (m.find()) {
            res = res.replaceAll("\\" + m.group(0), Character.toString((char) Integer.parseInt(m.group(1), 16)));
        }
        return res;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}