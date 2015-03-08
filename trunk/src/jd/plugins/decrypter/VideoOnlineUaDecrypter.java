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

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

//EmbedDecrypter 0.1.3
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "video.online.ua" }, urls = { "http://(www\\.)?video\\.online\\.ua/(embed/)?\\d+" }, flags = { 0 })
public class VideoOnlineUaDecrypter extends PluginForDecrypt {

    public VideoOnlineUaDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        // Allow adult videos
        br.setCookie("http://video.online.ua/", "online_18", "1");
        String parameter = param.toString();
        br.getPage(parameter);
        br.getRequest().setHtmlCode(br.toString().replace("\\", ""));
        String externID = br.getRegex("\"(http://megogo\\.net/e/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\\'(http://(www\\.)?1plus1\\.ua/[^<>\"]*?)\\'").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?(old\\.)?novy\\.tv/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(//(www\\.)?youtube\\.com/embed/[^<>\"]*?)\"").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("src=\\'https?:(//(www\\.)?youtube\\.com/embed/[A-Za-z0-9\\-_]+)\\'").getMatch(0);
        }
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http:" + externID));
            return decryptedLinks;
        }
        externID = br.getRegex("<embed src=\\'(https?://tsn\\.ua/bin/player/embed\\.php/[^<>\"]*?)\\'").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("value=\\'stream_url=(rtmp://stream\\.ictv\\.ua[^<>\"]*?)\\&amp;thumbnail_url=").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        final DownloadLink main = createDownloadlink("http://video.online.uadecrypted/" + new Regex(parameter, "(\\d+)$").getMatch(0));
        decryptedLinks.add(main);
        return decryptedLinks;
    }

}
