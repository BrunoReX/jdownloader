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
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "spi0n.com" }, urls = { "http://www\\.spi0n\\.com/[a-z0-9\\-_]+" }, flags = { 0 })
public class Spi0nCom extends PluginForDecrypt {

    public Spi0nCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String INVALIDLINKS = "http://www\\.spi0n\\.com/(favicon|\\d{4})";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        this.br.setAllowedResponseCodes(405);
        final String parameter = param.toString();
        if (parameter.matches(INVALIDLINKS)) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        br.getPage(parameter);
        if (this.br.getHttpConnection().getResponseCode() == 404) {
            logger.info("Link offline (no video on this page?!): " + parameter);
            final DownloadLink dl = this.createOfflinelink(parameter);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        final String[] regexes = new String[] { "\"((http:)?//(www\\.)?dailymotion\\.com/(embed/)?video/[^<>\"]*?)\"", "\"((https?:)?//(www\\.)?youtube\\.com/embed/[^<>\"/]+)\"", "\"(//player\\.vimeo\\.com/video/\\d+)" };
        for (final String regex : regexes) {
            final String[] results = br.getRegex(regex).getColumn(0);
            if (results != null && results.length != 0) {
                for (String result : results) {
                    if (!result.startsWith("http")) {
                        result = "http:" + result;
                    }
                    decryptedLinks.add(createDownloadlink(result));
                }
            }
        }
        if (decryptedLinks.size() > 0) {
            return decryptedLinks;
        }
        String finallink = null;
        /* Sometimes they host videos on their own servers */
        finallink = br.getRegex("\"file\":\"(http://(www\\.)?spi0n\\.com//?wp\\-content/uploads[^<>\"]*?)\"").getMatch(0);
        if (finallink != null) {
            finallink = "directhttp://" + finallink.replace("spi0n.com//", "spi0n.com/");
        }
        /* Maybe its a picture gallery */
        if (finallink == null) {
            final String fpName = br.getRegex("class=\"headline\">([^<>\"]*?)<").getMatch(0);
            String[] pictures = br.getRegex("size\\-(large|full) wp\\-image\\-\\d+\"( title=\"[^<>\"/]*?\")? alt=\"[^<>\"/]*?\" src=\"(http://(www\\.)?spi0n\\.com/wp\\-content/uploads/[^<>\"]*?)\"").getColumn(2);
            if (pictures == null || pictures.length == 0) {
                pictures = br.getRegex("size\\-(large|full) wp\\-image\\-\\d+\" title=\"[^<>\"/]+\" src=\"(http://(www\\.)?spi0n\\.com/wp\\-content/uploads/[^<>\"]*?)\"").getColumn(1);
            }
            if (pictures == null || pictures.length == 0) {
                pictures = br.getRegex("src=\"(http://(www\\.)?spi0n\\.com/wp\\-content/uploads/[^<>\"]*?)\" alt=\"").getColumn(0);
            }
            if (fpName == null || pictures == null || pictures.length == 0) {
                logger.info("Could not find any downloadable content!");
                return decryptedLinks;
            }
            for (final String pic : pictures) {
                final DownloadLink fina = createDownloadlink("directhttp://" + pic);
                fina.setAvailable(true);
                decryptedLinks.add(fina);
            }
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName).trim());
            fp.addLinks(decryptedLinks);
            return decryptedLinks;
        }
        decryptedLinks.add(createDownloadlink(finallink));

        return decryptedLinks;
    }

    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}