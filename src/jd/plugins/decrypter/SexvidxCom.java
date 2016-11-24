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
import java.util.LinkedHashSet;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "sexvidx.tv" }, urls = { "http://(sexvidx.tv|beejp.net)/[a-z0-9\\-/]*?/\\d+/[a-z0-9\\-]+\\.html" })
public class SexvidxCom extends antiDDoSForDecrypt {

    public SexvidxCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String filename = null;

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> crawledLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            crawledLinks.add(createOfflinelink(parameter));
            return crawledLinks;
        }
        if (parameter.contains("/info-movie/")) {
            String watchLink = br.getRegex("(https?://[^/]+/watch/movie.*?)\"").getMatch(0);
            logger.info("watchLink: " + watchLink);
        }
        filename = br.getRegex("top-title\">(?:Watch Online \\[Full Dvd\\] )?([^<>|]+)").getMatch(0);
        filename = Encoding.htmlDecode(filename.trim());
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(filename);
        final String[] watchLinks = br.getRegex("(/watch/movie[^\"]+)\"").getColumn(0);
        final LinkedHashSet<String> dupe = new LinkedHashSet<String>();
        for (final String watchLink : watchLinks) {
            if (!dupe.add(watchLink)) {
                continue;
            }
            logger.info("watchLink: " + watchLink);
            getPage(watchLink);
            crawlWatchLink(crawledLinks, parameter);
            fp.addLinks(crawledLinks);
        }
        if (watchLinks.length == 0) {
            crawlWatchLink(crawledLinks, parameter); // For current watchlink?
        }
        return crawledLinks;
    }

    private void crawlWatchLink(final ArrayList<DownloadLink> crawledLinks, final String parameter) throws Exception {
        String externID = null;
        // they deliver ads via iframes you can not just add one!
        final String[] iframes = br.getRegex("(<\\s*iframe\\s+[^>]*>.*?</iframe>|<\\s*iframe\\s+[^>]*\\s*/\\s*>)").getColumn(0);
        for (final String iframe : iframes) {
            externID = new Regex(iframe, "src=(\"|\')(https?.*?)(\"|\')").getMatch(1);
            if (!StringUtils.endsWithCaseInsensitive(externID, "jpg")) {
                if (externID == null) {
                    if (!br.containsHTML("s1\\.addParam\\(\\'flashvars\\'")) {
                        logger.info("Link offline: " + parameter);
                        continue; // return;
                    }
                    logger.warning("Decrypter broken for link: " + parameter);
                    continue; // return;
                }
                if (externID.contains("cloudtime.to/embed/")) {
                    final String vid = br.getRegex("https?://(www.)?cloudtime.to/embed/\\?v=(.*)").getMatch(1);
                    if (vid == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    externID = "http://www.cloudtime.to/video/" + vid;
                }
                logger.info("externID: " + externID);
                externID = Encoding.htmlDecode(externID);
                final DownloadLink dl = createDownloadlink(externID);
                dl.setFinalFileName(filename + ".mp4");
                crawledLinks.add(dl);
            }
        }
    }
}