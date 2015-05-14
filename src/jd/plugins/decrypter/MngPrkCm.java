//jDownloader - Downloadmanager
//Copyright (C) 2011  JD-Team support@jdownloader.org
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

import java.text.DecimalFormat;
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

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "mangapark.com" }, urls = { "http://(?:www\\.)?manga(?:park|tank|window)\\.(?:com|me)/manga/[\\w\\-\\.\\%]+/(?:s\\d/)?(?:v\\d+/?)?(?:c(?:ex(?:tra)?[^/]+|[\\d\\.]+(?:v\\d|[^/]+)?)?|extra(?:\\+\\d+)?|\\+\\(?:Oneshot\\))" }, flags = { 0 })
public class MngPrkCm extends PluginForDecrypt {
    /**
     * @author raztoki
     */

    // DEV NOTES
    // protocol: no https
    // other: sister sites mangatank & mangawindows.
    // other: links are not nessairly transferable
    // other: regex is tight as possible, be very careful playing!

    private String HOST = "";

    public MngPrkCm(PluginWrapper wrapper) {
        super(wrapper);
    }

    public void prepBrowser() {
        // define custom browser headers and language settings.
        br.getHeaders().put("Accept-Language", "en-gb, en;q=0.9, de;q=0.8");
        br.setCookie(HOST, "lang", "english");
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString().replace("://(?:www\\.)manga\\.(?:com|me)", "://mangamark.me");
        HOST = new Regex(parameter, "(https?://[^/]+)").getMatch(0);
        prepBrowser();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        if (br.getURL().contains("search?") || br.containsHTML("(>Sorry, the page you have requested cannot be found.<|Either the URL of your requested page is incorrect|page has been removed or moved to a new URL)")) {
            logger.info("Link offline: " + parameter);
            return decryptedLinks;
        }
        final Regex srv_info = br.getRegex("target=\"_blank\" href=\"(https?://(?:[a-z0-9]+\\.){1,}mpcdn\\.net/[^<>\"]*?)(\\d+)(\\.(?:jpg|png))(?:\\?\\d+)?\"");
        final String srv_link = srv_info.getMatch(0);
        final String extension = srv_info.getMatch(2);
        String[] fpname = br.getRegex(">([^<>\"]+)\\s*</a>\\s*/\\s*([^<>]+)<em class=\"refresh\"").getRow(0);
        if (srv_link == null || fpname == null || extension == null) {
            if (br.containsHTML("class=\"manga\"")) {
                logger.info("Link offline (unsupported link): " + parameter);
                return decryptedLinks;
            }
            logger.warning("Issue with getThis! : " + parameter);
            return null;
        }
        String fpName = (fpname[0] != null ? fpname[0] : "") + (fpname[1] != null ? " - " + fpname[1] : "");
        fpName = Encoding.htmlDecode(fpName).trim();
        // grab the total pages within viewer
        String totalPages = br.getRegex(">\\d+ of (\\d+)</a></em>").getMatch(0);
        if (totalPages == null) {
            totalPages = br.getRegex("selected>\\d+ / (\\d+)</option>").getMatch(0);
            if (totalPages == null) {
                // cound be on a chapter page //return null;
                String[] pages = srv_info.getColumn(-1);
                if (pages != null) {
                    totalPages = String.valueOf(pages.length);
                } else {
                    logger.warning("'TotalPages' not found! : " + parameter);
                    return null;
                }
            }
        }
        int numberOfPages = Integer.parseInt(totalPages);
        FilePackage fp = FilePackage.getInstance();
        fp.setProperty("CLEANUP_NAME", false);
        fp.setName(fpName);

        final DecimalFormat df = new DecimalFormat("00");

        for (int i = 1; i <= numberOfPages; i++) {
            final String img = srv_link + i + extension;
            final DownloadLink link = createDownloadlink("directhttp://" + img);
            link.setFinalFileName((fpName + " – page " + df.format(i) + extension).replace(" ", "_"));
            link.setAvailable(true);
            fp.add(link);
            decryptedLinks.add(link);
        }
        logger.warning("Task Complete! : " + parameter);
        HOST = "";
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}