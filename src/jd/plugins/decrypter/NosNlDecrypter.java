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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

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

import org.appwork.utils.formatter.TimeFormatter;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "nos.nl" }, urls = { "http://(www\\.)?nos\\.nl/(video/[A-Za-z0-9\\-_]+\\.html|/?embed/\\?id=v:\\d+|uitzendingen/(lq/)?\\d+)" }, flags = { 0 })
public class NosNlDecrypter extends PluginForDecrypt {

    public NosNlDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String           DOMAIN           = "nos.nl";

    private LinkedHashMap<String, String> FOUNDQUALITIES   = new LinkedHashMap<String, String>();
    private String                        FILENAME         = null;
    private String                        PARAMETER        = null;

    /** Settings stuff */
    private static final String           FASTLINKCHECK    = "FASTLINKCHECK";
    private static final String           ALLOW_LQ         = "ALLOW_LQ";
    private static final String           ALLOW_HQ         = "ALLOW_HQ";

    private static Object                 ctrlLock         = new Object();
    private static AtomicBoolean          pluginLoaded     = new AtomicBoolean(false);

    private String                        DATE             = null;
    private String                        VIDEOID          = null;

    private static final String           TYPE_VIDEO_EMBED = "http://nos\\.nl//?embed/\\?id=v:\\d+";
    private static final String           TYPE_VIDEO_VIDEO = "http://(www\\.)?nos\\.nl/video/[A-Za-z0-9\\-_]+\\.html";
    private static final String           TYPE_VIDEO_UITZ  = "http://(www\\.)?nos\\.nl/uitzendingen/(lq/)?\\d+";

    @SuppressWarnings("deprecation")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        PARAMETER = param.toString().replace("/lq/", "/");
        br.setFollowRedirects(true);
        synchronized (ctrlLock) {
            if (!pluginLoaded.get()) {
                // load plugin!
                JDUtilities.getPluginForHost(DOMAIN);
                pluginLoaded.set(true);
            }
            if (PARAMETER.matches(TYPE_VIDEO_EMBED)) {
                PARAMETER = "http://nos.nl/video/" + new Regex(PARAMETER, "(\\d+)$").getMatch(0) + "/";
            }
            br.getPage(PARAMETER);
            if (br.getHttpConnection().getResponseCode() == 404 || br.getURL().equals("http://nos.nl/uitzendingen/")) {
                final DownloadLink dl = createDownloadlink("directhttp://" + PARAMETER);
                dl.setProperty("offline", true);
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
            DATE = br.getRegex(">([^<>\"]*?)</time>").getMatch(0);
            VIDEOID = br.getRegex("nos\\.nl/[a-z]+/(\\d+)").getMatch(0);
            /* Decrypt start */
            FILENAME = br.getRegex("property=\"og:title\" content=\"([^<>]*?)\"").getMatch(0);
            if (FILENAME == null) {
                FILENAME = br.getRegex("<title>([^<>]*?)</title>").getMatch(0);
            }
            if (DATE == null || VIDEOID == null || FILENAME == null) {
                logger.warning("Decrypter broken for link: " + PARAMETER);
                return null;
            }
            DATE = Long.toString(TimeFormatter.getMilliSeconds(DATE, "dd-MM-yyyy, HH:mm", Locale.forLanguageTag("nl")));
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(FILENAME);

            /** Decrypt qualities START */
            String type = "video";
            String hqlink;
            if (PARAMETER.matches(TYPE_VIDEO_UITZ)) {
                type = "uitzending";
            }
            if (PARAMETER.matches("DO_NOT_USE_YET")) {
                br.getPage("http://content.nos.nl/content/playlist/uitzending/fragment/" + VIDEOID + ".xml");
                hqlink = br.getRegex("<location>(http://[^<>\"]*?)</location>").getMatch(0);
                if (hqlink == null) {
                    logger.warning("Decrypter broken for link: " + PARAMETER);
                    return null;
                }
                FOUNDQUALITIES.put("HQ", hqlink);
            } else {
                /* Previous handling removed AFTER revision 26337 */
                String lqlink = br.getRegex("\"(http://download\\.[^<>\"]*?)\" type=\"360p\"").getMatch(0);
                if (lqlink == null) {
                    lqlink = br.getRegex("\"(http://download\\.[^<>\"]*?)\"[^<>]*?360p\"").getMatch(0);
                }
                if (lqlink == null) {
                    logger.warning("Decrypter broken for link: " + PARAMETER);
                    return null;
                }
                lqlink = lqlink.replace("\\", "");
                FOUNDQUALITIES.put("LQ", lqlink);
                hqlink = br.getRegex("\"(http://download\\.[^<>\"]*?)\" type=\"480p\"").getMatch(0);
                if (hqlink == null) {
                    hqlink = br.getRegex("\"(http://download\\.[^<>\"]*?)\"[^<>]*?480p\"").getMatch(0);
                }
                if (hqlink != null) {
                    hqlink = hqlink.replace("\\", "");
                    FOUNDQUALITIES.put("HQ", hqlink);
                }
            }

            if (FOUNDQUALITIES == null) {
                logger.warning("Decrypter broken for link: " + PARAMETER);
                return null;
            }
            /** Decrypt qualities END */
            /** Decrypt qualities, selected by the user */
            final ArrayList<String> selectedQualities = new ArrayList<String>();
            final SubConfiguration cfg = SubConfiguration.getConfig(DOMAIN);
            /** User selected nothing -> Decrypt everything */
            boolean qlq = cfg.getBooleanProperty(ALLOW_LQ, false);
            boolean qhq = cfg.getBooleanProperty(ALLOW_HQ, false);
            if (qlq == false && qhq == false) {
                qlq = true;
                qhq = true;
            }
            if (qlq) {
                selectedQualities.add("LQ");
            }
            if (qhq) {
                selectedQualities.add("HQ");
            }
            for (final String selectedQualityValue : selectedQualities) {
                final DownloadLink dl = getVideoDownloadlink(selectedQualityValue);
                if (dl != null) {
                    fp.add(dl);
                    decryptedLinks.add(dl);
                }
            }
        }
        if (decryptedLinks.size() == 0) {
            logger.info(DOMAIN + ": None of the selected qualities were found, decrypting done...");
            return decryptedLinks;
        }
        return decryptedLinks;
    }

    @SuppressWarnings("deprecation")
    private DownloadLink getVideoDownloadlink(final String qualityValue) throws ParseException {
        final String directlink = FOUNDQUALITIES.get(qualityValue);
        if (directlink != null) {
            final String linkdupeid = DOMAIN + "_" + VIDEOID + "_" + FILENAME + "_" + qualityValue;
            final DownloadLink dl = createDownloadlink("http://nosdecrypted.nl/" + System.currentTimeMillis() + new Random().nextInt(10000));
            dl.setProperty("directlink", directlink);
            dl.setProperty("plain_qualityname", qualityValue);
            dl.setProperty("mainlink", PARAMETER);
            dl.setProperty("plain_filename", FILENAME);
            dl.setProperty("plain_date", DATE);
            dl.setProperty("plain_linkid", VIDEOID);
            dl.setProperty("plain_ext", directlink.substring(directlink.lastIndexOf(".")));
            try {
                dl.setContentUrl(PARAMETER);
                dl.setLinkID(linkdupeid);
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
                dl.setBrowserUrl(PARAMETER);
                dl.setProperty("LINKDUPEID", linkdupeid);
            }
            dl.setName(jd.plugins.hoster.NosNl.getFormattedFilename(dl));
            if (SubConfiguration.getConfig(DOMAIN).getBooleanProperty(FASTLINKCHECK, false)) {
                dl.setAvailable(true);
            }
            return dl;
        } else {
            return null;
        }
    }

    /**
     * JD2 CODE: DO NOIT USE OVERRIDE FÒR COMPATIBILITY REASONS!!!!!
     */
    public boolean isProxyRotationEnabledForLinkCrawler() {
        return false;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}