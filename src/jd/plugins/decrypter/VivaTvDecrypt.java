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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser.BrowserException;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.DummyScriptEnginePlugin;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "viva.tv", "mtviggy.com", "southpark.de", "southpark.cc.com", "vh1.com", "nickmom.com", "mtv.com.au", "mtv.com", "logotv.com" }, urls = { "http://www\\.viva\\.tv/charts/16\\-viva\\-top\\-100", "http://www\\.mtviggy\\.com/videos/[a-z0-9\\-]+/|http://www\\.mtvdesi\\.com/(videos/)?[a-z0-9\\-]+|http://www\\.mtvk\\.com/videos/[a-z0-9\\-]+", "http://www\\.southpark\\.de/alle\\-episoden/s\\d{2}e\\d{2}[a-z0-9\\-]+", "http://southpark\\.cc\\.com/full\\-episodes/s\\d{2}e\\d{2}[a-z0-9\\-]+", "http://www\\.vh1.com/(shows/[a-z0-9\\-_]+/[a-z0-9\\-_]+/.+|video/play\\.jhtml\\?id=\\d+|video/[a-z0-9\\-_]+/\\d+/[a-z0-9\\-_]+\\.jhtml|events/[a-z0-9\\-_]+/videos/[a-z0-9\\-_]+/\\d+/)", "http://www\\.nickmom\\.com/videos/[a-z0-9\\-]+/", "http://www\\.mtv\\.com\\.au/[a-z0-9\\-]+/videos/[a-z0-9\\-]+",
        "http://www\\.mtv\\.com/(shows/[a-z0-9\\-_]+/[^<>\"]+|[^<>\"]*?videos/.+)", "http://www\\.logotv.com/(video/[a-z0-9\\-]+/\\d+/[a-z0-9\\-]+\\.j?html|.+#id=\\d+$)" }, flags = { 0, 0, 0, 0, 0, 0, 0, 0, 0 })
public class VivaTvDecrypt extends PluginForDecrypt {

    public VivaTvDecrypt(PluginWrapper wrapper) {
        super(wrapper);
    }

    /** Tags: Viacom International Media Networks Northern Europe, mrss, gameone.de */
    /** Additional thanks goes to: https://github.com/rg3/youtube-dl/blob/master/youtube_dl/extractor/mtv.py */
    /* Additional information/methods can be found in the VivaTv host plugin */

    private static final String     type_viva                 = "http://www\\.viva\\.tv/.+";
    private static final String     type_mtviggy              = "http://www\\.mtviggy\\.com/videos/[a-z0-9\\-]+/";
    private static final String     type_mtvdesi              = "http://www\\.mtvdesi\\.com/(videos/)?[a-z0-9\\-]+";
    private static final String     type_mtvk                 = "http://www\\.mtvk\\.com/videos/[a-z0-9\\-]+";

    private static final String     type_southpark_de_episode = "http://www\\.southpark\\.de/alle\\-episoden/.+";
    private static final String     type_southpark_cc_episode = "http://southpark\\.cc\\.com/full\\-episodes/.+";

    private static final String     type_vh1                  = "http://www\\.vh1.com/.+";
    private static final String     subtype_vh1_episodes      = "http://www\\.vh1\\.com/shows/.+";
    private static final String     subtype_vh1_videos        = "http://www\\.vh1\\.com/video/.+";
    private static final String     subtype_vh1_events        = "http://www\\.vh1\\.com/events/.+";

    private static final String     type_nickmom_com          = "http://www\\.nickmom\\.com/videos/[a-z0-9\\-]+/";

    private static final String     type_mtv_com_au           = "http://www\\.mtv\\.com\\.au/.+";

    private static final String     type_mtv_com              = "http://www\\.mtv\\.com/.+";

    private static final String     type_logotv_com           = "http://www\\.logotv\\.com/.+";

    private ArrayList<DownloadLink> decryptedLinks            = new ArrayList<DownloadLink>();
    private String                  default_ext               = null;
    private String                  parameter                 = null;
    private String                  mgid                      = null;
    private String                  fpName                    = null;

    @SuppressWarnings("deprecation")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        /* we first have to load the plugin, before we can reference it */
        JDUtilities.getPluginForHost("viva.tv");
        default_ext = jd.plugins.hoster.VivaTv.default_ext;
        parameter = param.toString();
        br.setFollowRedirects(true);
        try {
            if (parameter.matches(type_viva)) {
                decryptVivaTvCharts();
            } else if (parameter.matches(type_mtviggy) || parameter.matches(type_mtvdesi) || parameter.matches(type_mtvk)) {
                decryptMtviggy();
            } else if (parameter.matches(type_southpark_de_episode)) {
                decryptSouthparkDe();
            } else if (parameter.matches(type_southpark_cc_episode)) {
                decryptSouthparkCc();
            } else if (parameter.matches(type_vh1)) {
                decryptVh1();
            } else if (parameter.matches(type_nickmom_com)) {
                decryptNickmomCom();
            } else if (parameter.matches(type_mtv_com_au)) {
                decryptMtvComAu();
            } else if (parameter.matches(type_mtv_com)) {
                decryptMtvCom();
            } else if (parameter.matches(type_logotv_com)) {
                decrypLogoTvCom();
            } else {
                /* Probably unsupported linktype */
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
        } catch (final BrowserException e) {
            final DownloadLink offline = createDownloadlink("directhttp://" + parameter);
            offline.setAvailable(false);
            offline.setProperty("offline", true);
            decryptedLinks.add(offline);
            return decryptedLinks;
        }
        return decryptedLinks;
    }

    private void decryptVivaTvCharts() throws DecrypterException, IOException {
        br.getPage(parameter);
        fpName = br.getRegex("<h1 class=\\'title\\'>([^<>\"]*?)</h1>").getMatch(0);
        if (fpName == null) {
            throw new DecrypterException("Decrypter broken for link: " + parameter);
        }
        final String allvids = br.getRegex("<div class=\\'related_episodes\\'>(.*?)</ul>[\t\n\r ]+</div>").getMatch(0);
        String[] chartinfo = new Regex(allvids, "<li (.*?)</a></li>").getColumn(0);
        for (final String chartnumber : chartinfo) {
            String title = new Regex(chartnumber, "title=\"([^<>]*?)\"").getMatch(0);
            final String url = new Regex(chartnumber, "data\\-url=\\'(https?://www\\.viva.tv/musikvideo/[^<>\"]*?)\\'").getMatch(0);
            if (title == null || url == null) {
                continue;
            }
            title = doFilenameEncoding(title) + default_ext;
            final DownloadLink dl = createDownloadlink(url);
            if (title.contains("Video nicht verfügbar")) {
                dl.setAvailable(false);
                title = title.replace("Video nicht verfügbar: ", "");
                dl.setFinalFileName(title);
            } else {
                dl.setAvailable(true);
                dl.setName(title);
            }
            decryptedLinks.add(dl);
        }
        if (decryptedLinks.size() == 0) {
            throw new DecrypterException("Decrypter broken for link: " + parameter);
        }
        final FilePackage fp = FilePackage.getInstance();
        fpName = Encoding.htmlDecode(fpName.trim());
        fp.setName(fpName);
        fp.addLinks(decryptedLinks);
    }

    private void decryptMtviggy() throws IOException {
        if (parameter.matches(type_mtvdesi) || parameter.matches(type_mtvk)) {
            parameter = "http://www.mtviggy.com/videos/" + new Regex(parameter, "([a-z0-9\\-]+)$").getMatch(0) + "/";
        }
        br.getPage(parameter);
        if (decryptVevo()) {
            return;
        }
        logger.info("Current link is NO VEVO link");
        final DownloadLink main = createDownloadlink(parameter.replace("mtviggy.com/", "mtviggy_jd_decrypted_jd_.com/"));
        if (!br.containsHTML("class=\"video\\-box\"") || br.getHttpConnection().getResponseCode() == 404) {
            main.setAvailable(false);
        } else {
            String filename = jd.plugins.hoster.VivaTv.getFilenameMTVIGGY(this.br);
            if (filename != null) {
                main.setName(filename + default_ext);
                main.setAvailable(true);
            }
        }
        decryptedLinks.add(main);

    }

    private void decryptSouthparkDe() throws IOException, DecrypterException {
        br.getPage(parameter);
        this.mgid = br.getRegex("media\\.mtvnservices\\.com/(mgid[^<>\"]*?)\"").getMatch(0);
        if (this.mgid == null) {
            throw new DecrypterException("Decrypter broken for link: " + parameter);
        }
        final String feedURL = String.format(getFEEDURL("southpark.de"), this.mgid);
        br.getPage(feedURL);
        fpName = getXML("title");
        if (fpName == null) {
            this.decryptedLinks = null;
            return;
        }
        fpName = new Regex(parameter, "episoden/(s\\d{2}e\\d{2})").getMatch(0) + " - " + fpName;
        fpName = Encoding.htmlDecode(fpName.trim());
        decryptFeed(getEMBEDURL("ALL_OTHERS"));
    }

    private void decryptSouthparkCc() throws IOException, DecrypterException {
        br.getPage(parameter);
        this.mgid = br.getRegex("data\\-mgid=\"(mgid[^<>\"]*?)\"").getMatch(0);
        if (this.mgid == null) {
            throw new DecrypterException("Decrypter broken for link: " + parameter);
        }
        final String feedURL = String.format(getFEEDURL("southpark.cc.com"), this.mgid);
        br.getPage(feedURL);
        fpName = getFEEDtitle(br.toString());
        if (fpName == null) {
            this.decryptedLinks = null;
            return;
        }
        fpName = new Regex(parameter, "episodes/(s\\d{2}e\\d{2})").getMatch(0) + " - " + fpName;
        fpName = Encoding.htmlDecode(fpName.trim());
        decryptFeed(getEMBEDURL("ALL_OTHERS"));
    }

    private void decryptVh1() throws DecrypterException, IOException {
        br.getPage(parameter);
        if (decryptVevo()) {
            return;
        }
        logger.info("Current link is NO VEVO link");
        final DownloadLink main = createDownloadlink(parameter.replace("vh1.com/", "vh1_jd_decrypted_jd_.com/"));
        if (!br.containsHTML("MTVN\\.VIDEO\\.PLAYER\\.instance") || br.getHttpConnection().getResponseCode() == 404) {
            main.setAvailable(false);
        } else {
            String filename = jd.plugins.hoster.VivaTv.getFilenameVH1(this.br);
            if (filename != null) {
                main.setName(filename + default_ext);
                main.setAvailable(true);
            }
        }
        decryptedLinks.add(main);
    }

    private void decryptNickmomCom() throws DecrypterException, IOException {
        br.getPage(parameter);
        String extern_ID = br.getRegex("\"(https?://(www\\.)?youtube\\.com/embed/[^<>\"]*?)\"").getMatch(0);
        if (extern_ID != null) {
            logger.info("Current link is an extern link");
            decryptedLinks.add(createDownloadlink(extern_ID));
            return;
        }
        logger.info("Current link is NO extern link");
        final DownloadLink main = createDownloadlink(parameter.replace("nickmom.com/", "nickmom_jd_decrypted_jd_.com/"));
        if (!br.containsHTML("class=\"video-player-wrapper\"") || br.getHttpConnection().getResponseCode() == 404) {
            main.setAvailable(false);
        } else {
            String filename = jd.plugins.hoster.VivaTv.getFilenameNickmomCom(this.br);
            if (filename != null) {
                main.setName(filename + default_ext);
                main.setAvailable(true);
            }
        }
        decryptedLinks.add(main);
    }

    /** This function should be able to decrypt any playlist- and single video from mtv.com.au. */
    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    private void decryptMtvComAu() throws Exception {
        br.getPage(parameter);
        final String playlist_id = br.getRegex("name=\"vimn:entity_uuid\" content=\"([a-z0-9\\-:]*?)\"").getMatch(0);
        final String js = br.getRegex("jQuery\\.extend\\(Drupal\\.settings, (\\{.*?)\\);.*?</script>").getMatch(0);
        if (js == null || playlist_id == null) {
            decryptedLinks = null;
            return;
        }
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(js);
        final LinkedHashMap<String, Object> vimn_video = (LinkedHashMap<String, Object>) entries.get("vimn_video");
        final Object playlist_o = vimn_video.get("playlists");
        if (playlist_o instanceof ArrayList) {
            /* Single video */
            entries = (LinkedHashMap<String, Object>) DummyScriptEnginePlugin.walkJson(entries, "vimn_videoplayer/" + playlist_id);
            final String part_mgid = (String) entries.get("video_id");
            final String partname = (String) entries.get("title");
            final String final_filename = this.doFilenameEncoding(partname) + this.default_ext;
            final DownloadLink fina = createDownloadlink("http://intl.mtvnservices.com/mrss/" + part_mgid + "/");
            fina.setFinalFileName(final_filename);
            fina.setProperty("decryptedfilename", final_filename);
            fina.setAvailable(true);
            try {
                fina.setContentUrl(this.parameter);
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
                fina.setBrowserUrl(this.parameter);
            }
            decryptedLinks.add(fina);
        } else {
            /* Playlist */
            entries = (LinkedHashMap<String, Object>) playlist_o;
            entries = (LinkedHashMap<String, Object>) entries.get(playlist_id);
            final ArrayList<Object> parts = (ArrayList) entries.get("items");
            final Object title_object = entries.get("title");
            if (title_object instanceof String) {
                fpName = (String) entries.get("title");
            }
            if (fpName == null) {
                fpName = new Regex(parameter, "mtv\\.com\\.au/([a-z0-9\\-_]+)/videos/.+").getMatch(0);
            }
            if (fpName == null) {
                logger.warning("fpName is null");
                decryptedLinks = null;
                return;
            }
            fpName = fpName != null ? doFilenameEncoding(fpName) : null;
            for (final Object pt : parts) {
                final LinkedHashMap<String, Object> playlistentry = (LinkedHashMap<String, Object>) pt;
                final String part_mgid = (String) playlistentry.get("guid");
                final String partname = (String) playlistentry.get("title");
                if (fpName == null) {
                    fpName = doFilenameEncoding(partname);
                }
                final String final_filename = this.doFilenameEncoding(partname) + this.default_ext;
                final DownloadLink fina = createDownloadlink("http://intl.mtvnservices.com/mrss/" + part_mgid + "/");
                fina.setFinalFileName(final_filename);
                fina.setProperty("decryptedfilename", final_filename);
                fina.setAvailable(true);
                try {
                    fina.setContentUrl(this.parameter);
                } catch (final Throwable e) {
                    /* Not available in old 0.9.581 Stable */
                    fina.setBrowserUrl(this.parameter);
                }
                decryptedLinks.add(fina);
            }
        }
        final FilePackage fp = FilePackage.getInstance();
        fpName = Encoding.htmlDecode(fpName.trim());
        fp.setName(fpName);
        fp.addLinks(decryptedLinks);
    }

    private void decryptMtvCom() throws Exception {
        final String feedURL_plain = this.getFEEDURL("mtv.com");
        br.getPage(parameter);
        if (decryptVevo()) {
            return;
        }
        logger.info("Current link is NO VEVO link");
        if (!br.containsHTML("MTVN\\.VIDEO\\.PLAYER\\.instance")) {
            try {
                decryptedLinks.add(this.createOfflinelink(parameter));
                return;
            } catch (final Throwable e) {
                return;
            }
        }
        this.mgid = br.getRegex("MTVN\\.VIDEO\\.contentUri = \\'(mgid:uma:[^<>\"]*?)\\';").getMatch(0);
        if (this.mgid == null) {
            this.decryptedLinks = null;
            return;
        }
        final String feedURL = String.format(feedURL_plain, this.mgid);
        br.getPage(feedURL);
        decryptFeed(feedURL_plain);
    }

    private void decrypLogoTvCom() throws Exception {
        boolean isPlaylist = false;
        final String playlist_id = new Regex(this.parameter, "^.+#id=(\\d+)$").getMatch(0);
        if (playlist_id != null) {
            /* Playlist */
            this.br.getPage("http://www.logotv.com/global/music/videos/ajax/playlist.jhtml?id=" + playlist_id);
            final String[] entries = this.br.getRegex("(id=\"vid\\d+\">.*?<p class=\"usage\"/>)").getColumn(0);
            if (entries != null && entries.length != 0) {
                isPlaylist = true;
                for (final String entry : entries) {
                    String title = new Regex(entry, "<span class=\"title_container trim_container\">[\t\n\r ]+<span>([^<>]*?)</span>").getMatch(0);
                    if (title == null) {
                        title = new Regex(entry, "class=\"song\">([^<>\"]*?)</span>").getMatch(0);
                    }
                    final String mgid = new Regex(entry, "logoonline\\.mtvnimages\\.com/uri/(mgid:[^<>\"\\?]+)").getMatch(0);
                    final String url_final = "http://media.mtvnservices.com/" + mgid;
                    final DownloadLink dl = this.createDownloadlink(url_final);
                    if (title == null) {
                        logger.warning("WTF");
                    }
                    if (title != null) {
                        /* Should cover 99% of all cases */
                        title = this.doFilenameEncoding(title);
                        dl.setName(title + default_ext);
                        dl.setAvailable(true);
                    }
                    this.decryptedLinks.add(dl);
                }
            }
        }
        if (!isPlaylist) {
            /* Feed */
            /* We have no feed-url so let's use this */
            final String feedURL_plain = "http://media.mtvnservices.com/%s";
            br.getPage(parameter);
            if (decryptVevo()) {
                return;
            }
            logger.info("Current link is NO VEVO link");
            if (!br.containsHTML("\"video\":")) {
                try {
                    decryptedLinks.add(this.createOfflinelink(parameter));
                    return;
                } catch (final Throwable e) {
                    return;
                }
            }
            this.mgid = br.getRegex("media\\.mtvnservices\\.com/(mgid[^<>\"]*?)\"").getMatch(0);
            if (this.mgid == null) {
                this.decryptedLinks = null;
                return;
            }
            final String feedURL = "http://www.logotv.com/player/includes/rss.jhtml?uri=" + this.mgid;
            br.getPage(feedURL);
            decryptFeed(feedURL_plain);

        }
    }

    private boolean decryptVevo() {
        final String vevo_ID = br.getRegex("MTVN\\.Player\\.vevoVideoId = \"([A-Za-z0-9]+)\";").getMatch(0);
        if (vevo_ID != null) {
            logger.info("Current link is a VEVO link");
            decryptedLinks.add(createDownloadlink("http://www.vevo.com/watch/" + vevo_ID));
            return true;
        }
        return false;
    }

    /** General function to decrypt viacom RSS feeds, especially with multiple segments of a single video no matter what their source is. */
    @SuppressWarnings("deprecation")
    private void decryptFeed(final String decrypter_url_plain) throws DecrypterException {
        final String[] items = br.getRegex("<item>(.*?)</item>").getColumn(0);
        if (fpName == null) {
            fpName = getMainFEEDTitle();
        }
        if (items == null || items.length == 0 || fpName == null) {
            throw new DecrypterException("Decrypter broken for link: " + parameter);
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);
        int counter = -1;
        for (final String item : items) {
            counter++;
            String title = getFEEDtitle(item);
            final String item_mgid = new Regex(item, "uri=(mgid:[A-Za-z0-9:\\-\\.]+)").getMatch(0);
            if (title == null || item_mgid == null) {
                throw new DecrypterException("Decrypter broken for link: " + parameter);
            }
            /* We don't need the intro - it's always the same! */
            if (counter == 0 && items.length > 1 && title.contains("Intro")) {
                continue;
            }
            title = doFilenameEncoding(title);
            title = title + this.default_ext;
            // feedURL_plain
            final String final_url = String.format(decrypter_url_plain, item_mgid);
            final DownloadLink dl = createDownloadlink(final_url);
            dl.setProperty("decryptedfilename", title);
            dl.setProperty("mainlink", this.parameter);
            dl.setName(title);
            dl.setAvailable(true);
            try {
                dl.setContentUrl(this.parameter);
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
                dl.setBrowserUrl(this.parameter);
            }
            decryptedLinks.add(dl);
        }
    }

    private String getMainFEEDTitle() {
        String title = getXML("title");
        if (title != null) {
            title = doEncoding(title);
        }
        return title;
    }

    private String getXML(final String parameter) {
        return getXML(this.br.toString(), parameter);
    }

    private String getXML(final String source, final String parameter) {
        return new Regex(source, "<" + parameter + "[^<]*?>([^<>]*?)</" + parameter + ">").getMatch(0);
    }

    private String getFEEDURL(final String domain) {
        return jd.plugins.hoster.VivaTv.feedURLs.get(domain);
    }

    private String getEMBEDURL(final String domain) {
        return jd.plugins.hoster.VivaTv.embedURLs.get(domain);
    }

    private String getFEEDtitle(final String source) {
        return jd.plugins.hoster.VivaTv.feedGetTitle(source);
    }

    private String doEncoding(final String data) {
        return jd.plugins.hoster.VivaTv.doEncoding(data);
    }

    private String doFilenameEncoding(final String filename) {
        return jd.plugins.hoster.VivaTv.doFilenameEncoding(filename);
    }

}
