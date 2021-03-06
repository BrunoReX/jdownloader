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
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "mangahost.com" }, urls = { "http://(?:www\\.)?(br\\.)?mangahost\\.(com|net)/manga/[^/]+/[^\\s]*\\d+(\\.\\d+)?" })
public class MangahostCom extends PluginForDecrypt {

    public MangahostCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 403) {
            logger.info("GEO-blocked!!");
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        } else if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String fpName = br.getRegex("<title>([^<>\"]+)</title>").getMatch(0);
        String[] links = null;
        if (br.containsHTML("var images")) {
            if (br.containsHTML("(jpg|png)\\.webp")) {
                links = br.getRegex("(https?://img\\.mangahost.net/br/images/[^<>\"\\']+\\.webp)").getColumn(0);
            } else {
                links = br.getRegex("(https?://img\\.mangahost.net/br/mangas_files/[^<>\"\\']+(jpg|png))").getColumn(0);
            }
        } else {
            String pages = br.getRegex("(var pages[^<>]+\\}\\]\\;)").getMatch(0);
            pages = pages.replace("\\/", "/");
            if (br.containsHTML("(jpg|png)\\.webp")) {
                links = new Regex(pages, "(https?://img\\.mangahost.net/br/images/[^<>\"\\']+\\.webp)").getColumn(0);
            } else {
                links = new Regex(pages, "(https?://img\\.mangahost.net/br/mangas_files/[^<>\"\\']+(jpg|png))").getColumn(0);
            }
        }
        if (links == null || links.length == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        for (String singleLink : links) {
            /* Correct final urls */
            singleLink = singleLink.replace("/images/", "/mangas_files/").replace(".webp", "");
            singleLink = "directhttp://" + singleLink;
            final DownloadLink dl = createDownloadlink(singleLink);
            dl.setAvailable(true);
            decryptedLinks.add(dl);
        }

        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }

        return decryptedLinks;
    }
}
