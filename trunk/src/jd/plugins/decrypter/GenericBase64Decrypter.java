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
import java.util.HashSet;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.HTMLParser;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

/**
 *
 * Decrypts base64 strings<br />
 * <br />
 *
 * NOTE: make sure the FIRST listening range is BASE64 otherwise this plugin will fail!
 *
 * @author raztoki
 *
 */
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "free.downloader.my", "saylicadebrid.tk", "binbox.io", "tinymoviez.info", "re-direcciona.me", "asianspankee.com", "mediaboom.org", "bookgn.com", "vip-files.net", "url4u.org", "tunesies.com", "xtragfx.com", "psdkeys.com" },

urls = { "https?://(?:\\w+\\.)?free\\.downloader\\.my/gateway\\.php\\?q=([a-zA-Z0-9_/\\+\\=\\-%]+)", "https?://(?:\\w+\\.)?saylicadebrid\\.tk/mali\\.php\\?reklamlar=([a-zA-Z0-9_/\\+\\=\\-%]+)", "https?://(?:\\w+\\.)?binbox\\.io/o/([a-zA-Z0-9_/\\+\\=\\-%]+)", "http://(?:www\\.)?tinymoviez\\.info/download\\.php\\?link=([a-zA-Z0-9_/\\+\\=\\-%]+)", "http://(?:www\\.)?re-direcciona\\.me/(?:I|r)/([a-zA-Z0-9_/\\+\\=\\-%]+)", "http://(?:www\\.)?asianspankee\\.com/.*\\?goto=([a-zA-Z0-9_/\\+\\=\\-%]+)", "http://(?:www\\.)?mediaboom\\.org/engine/go\\.php\\?url=([a-zA-Z0-9_/\\+\\=\\-%]+)", "http://(?:www\\.)?bookgn\\.com/engine/go\\.php\\?url=([a-zA-Z0-9_/\\+\\=\\-%]+)", "http://(?:www\\.)?vip-files\\.net/download\\.php\\?e=([a-zA-Z0-9_/\\+\\=\\-%]+)", "http://www\\.url4u\\.org/([a-zA-Z0-9_/\\+\\=\\-%]+)", "https?://(?:www\\.)?tunesies\\.com/go/([a-zA-Z0-9_/\\+\\=\\-%]+)",
        "https?://(?:www\\.)?xtragfx\\.com/engine/go\\.php\\?url=([a-zA-Z0-9_/\\+\\=\\-%]+)", "https?://(?:www\\.)?psdkeys\\.com/engine/go\\.php\\?url=([a-zA-Z0-9_/\\+\\=\\-%]+)" },

flags = { 0 })
public class GenericBase64Decrypter extends PluginForDecrypt {

    public GenericBase64Decrypter(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        final String b64 = Encoding.htmlDecode(new Regex(parameter, this.getSupportedLinks()).getMatch(0));
        if (b64 == null) {
            return null;
        }
        final HashSet<String> results = handleBase64Decode(b64);
        for (final String result : results) {
            decryptedLinks.add(createDownloadlink(result));
        }
        return decryptedLinks;
    }

    public final static HashSet<String> handleBase64Decode(final String b64) {
        final HashSet<String> results = new HashSet<String>();
        String finallink = b64;
        int i = 0;
        // this covers nested encoding.
        while (i < 20 && finallink != null && !finallink.matches("(?:ftp|https?)://.+")) {
            i++;
            // cleanup crap after padding. this can break subsequent tries
            finallink = Encoding.Base64Decode(finallink.replaceFirst("(={1,2})[\\w\\+]+$", "$1"));
        }
        // determine multi or single result?
        final String[] multi = new Regex(finallink, "(?:https?|ftp)://").getColumn(-1);
        if (multi != null && multi.length > 1) {
            // because links might not be separated or deliminated, its best to use htmlparser
            final String[] links = HTMLParser.getHttpLinks(finallink, "");
            if (links != null) {
                for (final String link : links) {
                    results.add(link);
                }
            }
        } else {
            results.add(finallink);
        }
        return results;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}