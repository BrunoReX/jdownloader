//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypter;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.gui.UserIO;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "gameone.de" }, urls = { "https?://((www|m)\\.)?gameone\\.de/(tv/\\d+(\\?part=\\d+)?|blog/\\d+/\\d+/.+|playtube/[\\d\\w\\-]+/\\d+(/(sd|hd))?)" }, flags = { 0 })
public class GameOneDeA extends PluginForDecrypt {

    public class ReplacerInputStream extends InputStream {

        private final byte[]      REPLACEMENT = "amp;".getBytes();
        private final byte[]      readBuf     = new byte[REPLACEMENT.length];
        private final Deque<Byte> backBuf     = new ArrayDeque<Byte>();
        private final InputStream in;

        /**
         * Replacing & to {@literal &amp;} in InputStreams
         *
         * @author mhaller
         * @see <a href="http://stackoverflow.com/a/4588005">http://stackoverflow.com/a/4588005</a>
         */
        /** Tags: Viacom International Media Networks Northern Europe, mrss, gameone.de */
        public ReplacerInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            if (!backBuf.isEmpty()) {
                return backBuf.pop();
            }
            int first = in.read();
            if (first == '&') {
                peekAndReplace();
            }
            return first;
        }

        private void peekAndReplace() throws IOException {
            int read = super.read(readBuf, 0, REPLACEMENT.length);
            for (int i1 = read - 1; i1 >= 0; i1--) {
                backBuf.push(readBuf[i1]);
            }
            for (int i = 0; i < REPLACEMENT.length; i++) {
                if (read != REPLACEMENT.length || readBuf[i] != REPLACEMENT[i]) {
                    for (int j = REPLACEMENT.length - 1; j >= 0; j--) {
                        // In reverse order
                        backBuf.push(REPLACEMENT[j]);
                    }
                    return;
                }
            }
        }

    }

    private Document doc;

    public GameOneDeA(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString().replace("https://", "http://");
        setBrowserExclusive();
        br.setFollowRedirects(false);
        br.setReadTimeout(60 * 1000);
        br.getPage(parameter);
        br.setFollowRedirects(true);
        if (br.getRedirectLocation() != null) {
            if (br.getHttpConnection().getResponseCode() == 302) {
                logger.info("Link offline or no downloadable content found: " + parameter);
                return decryptedLinks;
            } else if (br.getHttpConnection().getResponseCode() == 301) {
                br.getPage(parameter);
            }
        }

        // if (!br.containsHTML("\"player_swf\"")) {
        // logger.info("Wrong/Unsupported link: " + parameter);
        // return decryptedLinks;
        // }

        String dllink, filename;
        boolean newEpisode = true;
        final String episode = new Regex(parameter, "http://(www\\.)?gameone\\.de/tv/(\\d+)").getMatch(1);
        if (episode != null && Integer.parseInt(episode) < 102) {
            newEpisode = false;
        }

        final String[] mrssUrl = br.getRegex("\\.addVariable\\(\"mrss\"\\s?,\\s?\"(http://.*?)\"").getColumn(0);
        String fpName = br.getRegex("<title>(.*?)( \\||</title>)").getMatch(0);
        fpName = fpName == null ? br.getRegex("<h2>\n?(.*?)\n?</h2>").getMatch(0) : fpName;

        if (fpName == null) {
            return null;
        }

        fpName = fpName.replaceAll(" (-|~) Teil \\d+", "");
        fpName = fpName.replaceAll("\\.", "/");
        fpName = Encoding.htmlDecode(fpName);
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName.trim());

        /* audio, pictures */
        if (mrssUrl == null || mrssUrl.length == 0) {
            if (br.containsHTML("><img src=\"/images/dummys/dummy_agerated\\.jpg\"")) {
                UserIO.getInstance().requestMessageDialog(UserIO.STYLE_HTML, "Link momentan inaktiv", "<b><font color=\"red\">" + parameter + "</font></b><br /><br />Vollstängiger Inhalt zu o.g. Link ist zwischen 22:00 und 06:00 Uhr verfügbar!");
                return null;
            }
            String[] pictureOrAudio = null;
            if (br.containsHTML("<div class=\'gallery\' id=\\'gallery_\\d+\'")) {
                pictureOrAudio = br.getRegex("<div class=\'gallery_image\'>.*?<a href=\"(http://.*?/gallery_pictures/.*?/large/.*?)\"").getColumn(0);
            } else if (br.containsHTML("class=\"flash_container_audio\"")) {
                pictureOrAudio = br.getRegex("<a href=\"(http://[^<>]+\\.mp3)").getColumn(0);
                if (pictureOrAudio == null || pictureOrAudio.length == 0) {
                    pictureOrAudio = br.getRegex(",\\s?file:\\s?\"(http://[^<>\",]+)").getColumn(0);
                }
            }
            if (pictureOrAudio == null || pictureOrAudio.length == 0) {
                logger.warning("Decrypter out of date or no downloadable content found for link: " + parameter + ". Please check the Website!");
                return decryptedLinks;
            }
            if (pictureOrAudio.length <= 10) {
                newEpisode = false;
            }
            for (final String ap : pictureOrAudio) {
                final DownloadLink dlLink = createDownloadlink(ap);
                try {
                    if (pictureOrAudio.length > 1) {
                        dlLink.setContainerUrl(param.getCryptedUrl());
                    } else {
                        dlLink.setContentUrl(param.getCryptedUrl());
                    }
                } catch (Throwable e) {
                }
                if (newEpisode) {
                    dlLink.setAvailable(true);
                }
                fp.add(dlLink);
                decryptedLinks.add(dlLink);
            }
            return decryptedLinks;
        }

        /* video: blog, tv, playtube */
        for (String startUrl : mrssUrl) {
            startUrl = startUrl.replaceAll("http://(.*?)/", "http://www.gameone.de/api/mrss/");

            XPath xPath = xmlParser(startUrl);
            NodeList linkList, partList;
            XPathExpression expr = null;
            try {
                filename = xPath.evaluate("/rss/channel/item/title", doc);
                expr = xPath.compile("/rss/channel/item/group/content[@type='text/xml']/@url");
                partList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
                if (partList == null || partList.getLength() == 0) {
                    throw new Exception("PartList empty");
                }
            } catch (final Throwable e) {
                return null;
            }
            final DecimalFormat df = new DecimalFormat("000");
            for (int i = 0; i < partList.getLength(); ++i) {
                final Node partNode = partList.item(i);
                startUrl = partNode.getNodeValue();
                if (startUrl == null) {
                    continue;
                }
                /* Episode 1 - 101 */
                startUrl = startUrl.replaceAll("media/mediaGen\\.jhtml\\?uri.*?\\.de:", "flv/flvgen.jhtml?vid=");

                xPath = xmlParser(startUrl);
                try {
                    expr = xPath.compile("/package/video/item/src|//rendition/src");
                    linkList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
                    if (linkList == null || linkList.getLength() == 0) {
                        throw new Exception("LinkList empty");
                    }
                } catch (final Throwable e) {
                    continue;
                }
                String finalfilename = null;
                for (int j = 0; j < linkList.getLength(); ++j) {
                    final Node node = linkList.item(j);
                    dllink = node.getTextContent();
                    if (dllink == null) {
                        continue;
                    }
                    String q = new Regex(dllink, "(\\d+)k_").getMatch(0);
                    if (q == null) {
                        q = new Regex(dllink, "_\\d+x(\\d+)_").getMatch(0);
                    }
                    q = q == null ? "" : quality(Integer.parseInt(q));
                    String ext = dllink.substring(dllink.lastIndexOf("."));
                    ext = ext == null || ext.length() > 4 ? ".flv" : ext;

                    DownloadLink dlLink_rtmp = null;
                    boolean rtmpe = false;
                    if (dllink.startsWith("rtmpe://")) {
                        dlLink_rtmp = createDownloadlink("http://gameonedecrypted.de/" + System.currentTimeMillis() + new Random().nextInt(100000));
                        rtmpe = true;
                        dlLink_rtmp.setAvailable(false);
                        dlLink_rtmp.setProperty("offline", true);
                    }
                    DownloadLink dlLink_http = null;
                    if (dllink.startsWith("rtmp")) {
                        dlLink_rtmp = createDownloadlink("http://gameonedecrypted.de/" + System.currentTimeMillis() + new Random().nextInt(100000));
                        dlLink_rtmp.setProperty("mainlink", parameter);
                        dlLink_rtmp.setProperty("directlink", dllink);
                        try {
                            dlLink_rtmp.setContentUrl(parameter);
                        } catch (final Throwable e) {
                            /* Not available in old 0.9.581 Stable */
                            dlLink_rtmp.setBrowserUrl(parameter);
                        }
                    }
                    /* Episode > 102 */
                    dllink = dllink.replaceAll("^.*?/r2/", "http://cdn.riptide-mtvn.com/r2/");
                    if (dllink.startsWith("http://")) {
                        dlLink_http = createDownloadlink("http://gameonedecrypted.de/" + System.currentTimeMillis() + new Random().nextInt(100000));
                        dlLink_http.setProperty("mainlink", parameter);
                        dlLink_http.setProperty("directlink", dllink);
                        try {
                            dlLink_http.setContentUrl(parameter);
                        } catch (final Throwable e) {
                            /* Not available in old 0.9.581 Stable */
                            dlLink_http.setBrowserUrl(parameter);
                        }
                    }
                    if (!newEpisode) {
                        if (dlLink_http != null) {
                            finalfilename = filename + "_Part_" + df.format(i + 1) + "@" + q + "_http" + ext;
                            dlLink_http.setFinalFileName(finalfilename);
                            dlLink_http.setProperty("LINKDUPEID", "gameone_" + finalfilename);
                        }
                        if (dlLink_rtmp != null) {
                            finalfilename = filename + "_Part_" + df.format(i + 1) + "@" + q + "_rtmp" + (rtmpe ? "e" : "") + ext;
                            dlLink_rtmp.setFinalFileName(finalfilename);
                            dlLink_rtmp.setProperty("LINKDUPEID", "gameone_" + finalfilename);
                        }
                    } else {
                        if (dlLink_http != null) {
                            finalfilename = filename + "@" + q + "_http" + ext;
                            dlLink_http.setFinalFileName(finalfilename);
                            dlLink_http.setProperty("LINKDUPEID", "gameone_" + finalfilename);
                        }
                        if (dlLink_rtmp != null) {
                            finalfilename = filename + "@" + q + "_rtmp" + (rtmpe ? "e" : "") + ext;
                            dlLink_rtmp.setFinalFileName(finalfilename);
                            dlLink_rtmp.setProperty("LINKDUPEID", "gameone_" + finalfilename);
                        }
                    }
                    if (dlLink_http != null) {
                        fp.add(dlLink_http);
                        try {
                            dlLink_http.setContainerUrl(parameter);
                        } catch (Throwable e) {
                        }
                        decryptedLinks.add(dlLink_http);
                    }
                    if (dlLink_rtmp != null) {
                        fp.add(dlLink_rtmp);

                        try {
                            dlLink_rtmp.setContainerUrl(parameter);
                        } catch (Throwable e) {
                        }
                        decryptedLinks.add(dlLink_rtmp);
                    }
                }
            }
        }
        if (decryptedLinks == null || decryptedLinks.size() == 0) {
            logger.warning("Decrypter out of date for link: " + parameter);
            return null;
        }

        return decryptedLinks;
    }

    private String quality(final int q) {
        if (q <= 350) {
            return "LOW";
        }
        if (q < 720) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private XPath xmlParser(final String linkurl) throws Exception {
        URLConnectionAdapter con = null;
        try {
            con = new Browser().openGetConnection(linkurl);
            final DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            final XPath xPath = XPathFactory.newInstance().newXPath();
            try {
                doc = parser.parse(new ReplacerInputStream(con.getInputStream()));
                return xPath;
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        } catch (final Throwable e2) {
            return null;
        }
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}