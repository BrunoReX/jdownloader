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
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.SiteType.SiteTemplate;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "rule34.paheal.net" }, urls = { "http://(www\\.)?rule34\\.paheal\\.net/post/(list/[\\w\\-\\.%!]+|view)/\\d+" }, flags = { 0 })
public class Rule34PahealNet extends PluginForDecrypt {

    public Rule34PahealNet(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML(">No Images Found<")) {
            decryptedLinks.add(createOfflinelink(parameter, "Offline Content"));
            return decryptedLinks;
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(new Regex(parameter, "rule34\\.paheal\\.net/post/list/(.*?)/\\d+").getMatch(0));
        String next = null;
        do {
            if (this.isAbort()) {
                logger.info("Decryption aborted by user");
                return decryptedLinks;
            }
            if (next != null) {
                br.getPage(next);
            }
            String[] links = br.getRegex("<br><a href=('|\")(http://.*?)\\1>").getColumn(1);
            if (links == null || links.length == 0) {
                links = br.getRegex("('|\")(http://rule34-images\\.paheal\\.net/_images/[a-z0-9]+/.*?)\\1").getColumn(1);
            }
            if (links == null || links.length == 0) {
                links = br.getRegex("('|\")(http://rule34-[a-zA-Z0-9\\-]*?\\.paheal\\.net/_images/[a-z0-9]+/.*?)\\1").getColumn(1);
            }
            if (links == null || links.length == 0) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            for (final String singlelink : links) {
                final DownloadLink dl = createDownloadlink("directhttp://" + singlelink);
                dl.setAvailable(true);
                fp.add(dl);
                decryptedLinks.add(dl);
                distribute(dl);
            }
            next = br.getRegex("\"(/post/[^<>\"]*?)\">Next</a>").getMatch(0);
        } while (next != null);
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.Danbooru;
    }

}