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

import org.appwork.utils.formatter.SizeFormatter;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "files.fm" }, urls = { "https?://(?:www\\.)?files\\.fm/u/[a-z0-9]+" }) 
public class FilesFmFolder extends PluginForDecrypt {

    public FilesFmFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        /* 2016-03-10: They enforce https */
        final String parameter = param.toString().replace("http://", "https://");
        final String fid = new Regex(parameter, "([a-z0-9]+)$").getMatch(0);
        this.br.setFollowRedirects(true);
        br.getPage("http://files.fm/u/" + fid + "?view=gallery&items_only=true&index=0&count=10000");
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML(">This link does not contain any files|These files are deleted by the owner<|The expiry date of these files is over<|class=\"deleted_wrapper\"")) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String fpName = null;
        String[] links = br.getRegex("<div class=\"hover_items\">(.*?)class=\"OrderID\"").getColumn(0);
        if (links == null || links.length == 0) {
            links = br.getRegex("class=\"file\\-icon\"(.*?)class=\"OrderID\"").getColumn(0);
        }
        if (links == null || links.length == 0) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        for (final String singleLink : links) {
            String filename = new Regex(singleLink, "\\&n=([^<>\"]*?)(?:\\')?\"").getMatch(0);
            if (filename == null) {
                filename = new Regex(singleLink, "\\&n=([^<>\"]*?)\\'").getMatch(0);
            }
            final String filesize = new Regex(singleLink, "class=\"file_size\">([^<>}\"]*?)<").getMatch(0);
            final String fileid = new Regex(singleLink, "\\?i=([a-z0-9]+)").getMatch(0);
            if (filename == null || filesize == null || fileid == null) {
                return null;
            }
            final String contentUrl = "https://files.fm/down.php?i=" + fileid + "&n=" + filename;
            final DownloadLink dl = createDownloadlink(contentUrl);
            dl.setProperty("mainlink", parameter);
            dl.setContentUrl(contentUrl);
            dl.setLinkID(fileid);
            dl.setAvailable(true);
            dl.setFinalFileName(Encoding.htmlDecode(filename));
            dl.setDownloadSize(SizeFormatter.getSize(Encoding.htmlDecode(filesize)));
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
