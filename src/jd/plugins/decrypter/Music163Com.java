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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Random;

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

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "music.163.com" }, urls = { "http://(www\\.)?music\\.163\\.com/(?:#/)?(?:album\\?id=\\d+|artist/album\\?id=\\d+)" }, flags = { 0 })
public class Music163Com extends PluginForDecrypt {

    public Music163Com(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String TYPE_SINGLE_ALBUM = "http://(www\\.)?music\\.163\\.com/(?:#/)?album\\?id=\\d+";
    private static final String TYPE_ARTIST       = "http://(www\\.)?music\\.163\\.com/(?:#/)?artist/album\\?id=\\d+";

    /** Settings stuff */
    private static final String FAST_LINKCHECK    = "FAST_LINKCHECK";
    private static final String GRAB_COVER        = "GRAB_COVER";

    /* Other possible API calls: http://music.163.com/api/playlist/detail?id=%s http://music.163.com/api/artist/%s */
    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        /* Load sister host plugin */
        JDUtilities.getPluginForHost("music.163.com");
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        final String lid = new Regex(parameter, "(\\d+)$").getMatch(0);
        String formattedDate = null;
        final SubConfiguration cfg = SubConfiguration.getConfig("music.163.com");
        final boolean fastcheck = cfg.getBooleanProperty(FAST_LINKCHECK, false);
        final String[] qualities = jd.plugins.hoster.Music163Com.audio_qualities;
        LinkedHashMap<String, Object> entries = null;
        ArrayList<Object> resourcelist = null;
        jd.plugins.hoster.Music163Com.prepareAPI(this.br);
        if (parameter.matches(TYPE_ARTIST)) {
            br.getPage("http://music.163.com/api/artist/albums/" + lid + "?id=" + lid + "&offset=0&total=true&limit=1000");
            if (br.getHttpConnection().getResponseCode() != 200) {
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
            resourcelist = (ArrayList) entries.get("hotAlbums");
            for (final Object albumo : resourcelist) {
                final LinkedHashMap<String, Object> album_info = (LinkedHashMap<String, Object>) albumo;
                final String album_id = Long.toString(jd.plugins.hoster.DummyScriptEnginePlugin.toLong(album_info.get("id"), -1));
                final DownloadLink dl = createDownloadlink("http://music.163.com/album?id=" + album_id);
                decryptedLinks.add(dl);
            }
        } else {
            br.getPage("http://music.163.com/api/album/" + lid + "/");
            if (br.getHttpConnection().getResponseCode() != 200) {
                decryptedLinks.add(this.createOfflinelink(parameter));
                return decryptedLinks;
            }
            entries = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
            entries = (LinkedHashMap<String, Object>) entries.get("album");
            final String coverurl = (String) entries.get("picUrl");
            final long publishedTimestamp = jd.plugins.hoster.DummyScriptEnginePlugin.toLong(entries.get("publishTime"), 0);
            LinkedHashMap<String, Object> artistinfo = (LinkedHashMap<String, Object>) entries.get("artist");
            resourcelist = (ArrayList) entries.get("songs");
            final String name_artist = (String) artistinfo.get("name");
            final String name_album = (String) entries.get("name");
            String fpName = name_artist + " - " + name_album;
            int counter = 1;
            if (publishedTimestamp > 0) {
                final SimpleDateFormat formatter = new SimpleDateFormat(jd.plugins.hoster.Music163Com.dateformat_en);
                formattedDate = formatter.format(publishedTimestamp);
            }
            for (final Object songo : resourcelist) {
                String ext = null;
                long filesize = 0;
                final LinkedHashMap<String, Object> song_info = (LinkedHashMap<String, Object>) songo;
                final String songname = (String) song_info.get("name");
                final String fid = Long.toString(jd.plugins.hoster.DummyScriptEnginePlugin.toLong(song_info.get("id"), -1));
                /* Now find the highest quality available */
                for (final String quality : qualities) {
                    final Object musicO = song_info.get(quality);
                    if (musicO != null) {
                        final LinkedHashMap<String, Object> musicmap = (LinkedHashMap<String, Object>) musicO;
                        ext = (String) musicmap.get("extension");
                        filesize = jd.plugins.hoster.DummyScriptEnginePlugin.toLong(musicmap.get("size"), -1);
                        break;
                    }
                }

                if (ext == null || songname == null || fid.equals("-1") || filesize == -1) {
                    return null;
                }
                final DownloadLink dl = createDownloadlink("http://music.163.com/song?id=" + fid);
                String filename = counter + "." + name_artist + " - " + name_album + " - " + songname + "." + ext;
                if (formattedDate != null) {
                    dl.setProperty("publishedTimestamp", publishedTimestamp);
                    filename = formattedDate + "_" + filename;
                }
                dl.setLinkID(fid);
                dl.setFinalFileName(filename);
                dl.setProperty("directfilename", filename);
                dl.setProperty("trachnumber", Integer.toString(counter));
                dl.setAvailable(true);
                dl.setDownloadSize(filesize);
                decryptedLinks.add(dl);
                counter++;
            }
            if (cfg.getBooleanProperty(GRAB_COVER, false) && coverurl != null) {
                final DownloadLink dlcover = createDownloadlink("decrypted://music.163.comcover" + System.currentTimeMillis() + new Random().nextInt(1000000000));
                String filenamecover = fpName;
                if (formattedDate != null) {
                    filenamecover = formattedDate + "_" + filenamecover;
                }
                String ext = coverurl.substring(coverurl.lastIndexOf("."));
                if (ext.length() > 5) {
                    ext = ".jpg";
                }
                filenamecover += ext;
                if (fastcheck) {
                    dlcover.setAvailable(true);
                }
                dlcover.setFinalFileName(filenamecover);
                dlcover.setContentUrl(parameter);
                dlcover.setProperty("directfilename", filenamecover);
                dlcover.setProperty("mainlink", parameter);
                dlcover.setProperty("directlink", coverurl);
                decryptedLinks.add(dlcover);
            }
            if (br.getHttpConnection().getResponseCode() == 404) {
                try {
                    decryptedLinks.add(this.createOfflinelink(parameter));
                } catch (final Throwable e) {
                    /* Not available in old 0.9.581 Stable */
                }
                return decryptedLinks;
            }
            if (formattedDate != null) {
                fpName = formattedDate + " - " + fpName;
            }
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(fpName);
            fp.addLinks(decryptedLinks);
        }

        return decryptedLinks;
    }
}
