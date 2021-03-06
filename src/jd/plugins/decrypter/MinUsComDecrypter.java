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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "minus.com" }, urls = { "http://([a-zA-Z0-9]+\\.)?(minus\\.com|min\\.us)/[A-Za-z0-9]{2,}((?:_[a-z]{1,2})?\\.(?:bmp|png|jpe?g|gif))?" }) 
public class MinUsComDecrypter extends PluginForDecrypt {

    public MinUsComDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private final String       INVALIDLINKS  = "https?://([a-zA-Z0-9]+\\.)?(minus\\.com|min\\.us)/(directory|explore|httpsmobile|pref|recent|search|smedia|uploads|mobile|app)";
    private final String       INVALIDLINKS2 = "https?://(www\\.)?blog\\.(minus\\.com|min\\.us)/.+";

    public static final String TYPE_DIRECT   = "https?://i\\.minus\\.com/[^<>\"/]+\\.[A-Za-z]{3,5}";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        // they have subdomains like userprofile.minus.com/
        String parameter = param.toString().replace("dev.min", "min").replace("min.us/", "minus.com/");
        /*
         * 2015-09-02 -psp-: wtf please verify the 4 lines below - they will definitly make this fail:
         * http://frontalspy.minus.com/mCQMjf78hqVOv
         */
        // image is downloadable and we can no longer seem to convert it back to standard link -raztoki 20150511
        // if (parameter.matches("(?i).+/[A-Za-z0-9]{2,}((?:_[a-z]{1,2})?\\.(?:bmp|png|jpe?g|gif))?")) {
        // decryptedLinks.add(createDownloadlink("directhttp://" + parameter));
        // return decryptedLinks;
        // }

        final String fid = new Regex(parameter, "minus\\.com/(.+)").getMatch(0);

        if (parameter.matches(TYPE_DIRECT)) {
            decryptedLinks.add(createDownloadlink(parameter.replace("minus.com/", "minusdecrypted.com/")));
            return decryptedLinks;
        }

        // ignore trash here... uses less memory allocations
        if (parameter.matches(INVALIDLINKS) || parameter.matches(INVALIDLINKS2)) {
            final DownloadLink dl = createDownloadlink(parameter.replace("minus.com/", "minusdecrypted.com/"));
            dl.setAvailable(false);
            dl.setProperty("offline", true);
            dl.setName(new Regex(parameter, "minus\\.com/(.+)").getMatch(0));
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        // some link types end up been caught, like directlinks or alternative links, lets correct these to be all the same.
        // String[] fuid = new Regex(parameter, "(minus\\.com|min\\.us)/([A-Za-z0-9]{13,14})").getRow(0);
        // if (fuid != null && fuid.length == 2) {
        // fuid[1] = fuid[1].replaceFirst("[a-z]", "");
        // parameter = "http://" + fuid[0] + "/l" + fuid[1];
        // }

        br.setFollowRedirects(false);
        try {
            br.getPage(parameter);
        } catch (final Throwable e) {
            final DownloadLink dl = createDownloadlink(parameter.replace("minus.com/", "minusdecrypted.com/"));
            dl.setAvailable(false);
            dl.setProperty("offline", true);
            dl.setName(new Regex(parameter, "minus\\.com/(.+)").getMatch(0));
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("(<h2>Not found\\.</h2>|<p>Our records indicate that the gallery/image you are referencing has been deleted or does not exist|The page you requested does not exist)") || br.containsHTML("\"items\": \\[\\]") || br.containsHTML("class=\"guesthomepage_cisi_h1\">Upload and share your files instantly") || br.containsHTML(">The folder you requested has been deleted or has expired") || br.containsHTML(">You\\'re invited to join Minus") || br.containsHTML("<title>Error \\- Minus</title>")) {
            final DownloadLink dl = createDownloadlink(parameter.replace("minus.com/", "minusdecrypted.com/"));
            dl.setAvailable(false);
            dl.setProperty("offline", true);
            dl.setName(new Regex(parameter, "minus\\.com/(.+)").getMatch(0));
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        if (br.getRedirectLocation() != null) {
            final DownloadLink dl = createDownloadlink(parameter.replace("minus.com/", "minusdecrypted.com/"));
            dl.setAvailable(false);
            dl.setProperty("offline", true);
            dl.setName(new Regex(parameter, "minus\\.com/(.+)").getMatch(0));
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        // Get album name for package name
        String fpName = br.getRegex("<title>(.*?) \\- Minus</title>").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("var gallerydata = \\{.+ \"name\": \"([^\"]+)").getMatch(0);
        }
        if (fpName == null) {
            fpName = fid;
        }
        if (fpName == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        fpName = decodeUnicode(Encoding.htmlDecode(fpName.trim()));
        // do not catch first "name", only items within array
        String[] items = br.getRegex("\\{([^}{]*?\"name\": \"[^\"]+?\"[^}{]*?)\\}").getColumn(0);
        // fail over for single items ?. Either that or they changed website yet
        // again and do not display the full gallery array.
        if (items == null || items.length == 0) {
            items = br.getRegex("var gallerydata = \\{(.*?)\\};").getColumn(0);
        }
        if (items != null && items.length != 0) {
            for (String singleitems : items) {
                String filename = new Regex(singleitems, "\"name\": ?\"([^<>\"]*?)\"").getMatch(0);
                final String filesize = new Regex(singleitems, "\"filesize_bytes\": ?(\\d+)").getMatch(0);
                final String secureprefix = new Regex(singleitems, "\"secure_prefix\": ?\"(/\\d+/[A-Za-z0-9\\-_]+)\"").getMatch(0);
                final String linkid = new Regex(singleitems, "\"id\": ?\"([A-Za-z0-9\\-_]+)\"").getMatch(0);
                if (filename == null || filesize == null || secureprefix == null || linkid == null) {
                    logger.warning("Decrypter broken for link: " + parameter);
                    return null;
                }
                filename = decodeUnicode(Encoding.htmlDecode(filename.trim()));
                if (!filename.startsWith("/")) {
                    filename = "/" + filename;
                }
                final String filelink = "http://minusdecrypted.com/l" + linkid;
                final DownloadLink dl = createDownloadlink(filelink);
                dl.setFinalFileName(filename);
                dl.setDownloadSize(Long.parseLong(filesize));
                dl.setAvailable(true);
                decryptedLinks.add(dl);
            }
        } else {
            // Only one link available, add it!
            final String filesize = br.getRegex("<div class=\"item-actions-right\">[\t\n\r ]+<a title=\"([^<>\"]*?)\"").getMatch(0);
            final DownloadLink dl = createDownloadlink(parameter.replace("minus.com/", "minusdecrypted.com/"));
            if (filesize != null) {
                dl.setDownloadSize(SizeFormatter.getSize(filesize));
            }
            decryptedLinks.add(dl);
        }
        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(fpName);
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    private String decodeUnicode(final String s) {
        final Pattern p = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        String res = s;
        final Matcher m = p.matcher(res);
        while (m.find()) {
            res = res.replaceAll("\\" + m.group(0), Character.toString((char) Integer.parseInt(m.group(1), 16)));
        }
        return res;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}