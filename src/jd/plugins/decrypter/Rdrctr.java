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

import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.gui.UserIO;
import jd.parser.html.Form;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = {}, urls = {}, flags = {})
public class Rdrctr extends PluginForDecrypt {
    /**
     * Returns the annotations names array
     * 
     * @return
     */
    public static String[] getAnnotationNames() {
        return new String[] { "Redirecter Services" };
    }

    /**
     * returns the annotation pattern array
     * 
     * @return
     */
    public static String[] getAnnotationUrls() {
        StringBuilder completePattern = new StringBuilder();
        String[] list = { "http://[\\w\\.]*?readthis\\.ca(/[a-zA-Z0-9]+)?", "http://[\\w\\.]*?redirects\\.ca(/[a-zA-Z0-9]+)?", "http://[\\w\\.]*?goshrink\\.com(/[a-zA-Z0-9]+)?", "http://[\\w\\.]*?clickthru\\.ca(/[a-zA-Z0-9]+)?", "http://[\\w\\.]*?atu\\.ca(/[a-zA-Z0-9]+)?", "http://[\\w\\.]*?easyurl\\.net(/[a-zA-Z0-9]+)?", "http://[\\w\\.]*?fyad\\.org(/[a-zA-Z0-9]+)?", "http://[\\w\\.]*?is\\.gd(/[a-zA-Z0-9]+)?", "http://[\\w\\.]*?redirect\\.wayaround\\.org/[a-zA-Z0-9]+/(.*)", "http://[\\w\\.]*?rurl\\.org(/[a-zA-Z0-9]+)?", "http://[\\w\\.]*?tinyurl\\.com/[a-zA-Z0-9\\-]+", "http://[\\w\\.]*?smarturl\\.eu/\\?[a-zA-Z0-9]+", "http://[\\w\\.]*?linkmize\\.com(/[a-zA-Z0-9]+)?", "http://go2\\.u6e\\.de/[a-zA-Z0-9]+", "http://[\\w\\.]*?shrinkify\\.com/[a-zA-Z0-9]+", "http://[\\w\\.]*?s7y\\.com/[a-zA-Z0-9]+", "http://[\\w\\.]*?rln\\.me/[0-9a-zA-Z]+", "http://[\\w\\.]*?sp2\\.ro/[0-9a-zA-Z]+",
                "http://[\\w\\.]*?s7y.us/[a-zA-Z0-9]+", "http://[\\w\\.]*?ow\\.ly/[\\w]+", "http://[\\w\\.]*?bit\\.ly/[\\w]+", "http://[\\w\\.]*?ponyurl\\.com/[\\w]+", "http://skracaj\\.org/[\\w]+", "http://l-x\\.pl/[\\w]+", "http://[\\w\\.]*?budurl\\.com/[a-zA-Z0-9]+", "http://[\\w\\.]*?yep\\.it/.+", "http://[\\w\\.]*?urlite\\.com/[\\d]+", "http://[\\w\\.]*?urlcini\\.com/[\\w]+", "http://[\\w\\.]*?dwarfurl\\.com/.*", "http://[\\w\\.]*?tra\\.kz/[\\w_\\-,()*:]+", "http://[\\w\\.]*?tiny\\.pl/[\\w]+", "http://[\\w\\.]*?tiny\\.cc/[0-9a-zA-Z]+", "http://[\\w\\.]*?clop\\.in/[a-zA-Z0-9]+", "http://[\\w\\.]*?linkth\\.at/[a-z]+", "http://[\\w\\.]*?adf\\.ly/[0-9a-zA-Z]+", "http://[\\w\\.]*?url(Pass|pass)\\.com/[0-9a-z]+", "http://[\\w\\.]*?bacn\\.me/[0-9a-z]+", "http://[\\w\\.]*?moourl\\.com/[a-zA-Z0-9]+", "http://[\\w\\.]*?(cli|cl)\\.gs/[a-zA-Z0-9]+",
                "http://[\\w\\.]*?thnlnk\\.com/[a-zA-Z0-9]+/[a-zA-Z0-9]+/[0-9]+", "http://[\\w\\.]*?editurl\\.com/[a-z0-9]+", "http://[\\w\\.]*?ri\\.ms/[a-z0-9]+", "http://[\\w\\.]*?gelen\\.org/[0-9]+", "http://[\\w\\.]*?stotr\\.info//[0-9a-z]+", "http://[\\w\\.]*?(kingurl|shrinkurl|happyurl|tinyhiny|url-url)\\.com/[0-9a-z]+", "http://[\\w\\.]*?pettyurl\\.com/[0-9a-z]+", "http://[\\w\\.]*?9mmo\\.com/[a-z0-9]+", "http://[\\w\\.]*?bmi-group\\.99k\\.org/go\\.php\\?id=[0-9]+", "http://[\\w\\.]*?\\.decenturl\\.com/[a-z0-9.-]+", "http://[\\w\\.]*?shor7\\.com/\\?[A-Z]+", "http://[\\w\\.]*?f5y\\.info/[a-zA-Z-]+", "http://[\\w\\.]*?starturl\\.com/[a-z_]+", "http://[\\w\\.]*?plzlink\\.me/[0-9a-z]+" };
        for (String pattern : list) {
            if (completePattern.length() > 0) {
                completePattern.append("|");
            }
            completePattern.append(pattern);
        }
        logger.finest("Redirecter: " + list.length + " Pattern added!");
        return new String[] { completePattern.toString() };
    }

    /**
     * Returns the annotations flags array
     * 
     * @return
     */
    public static int[] getAnnotationFlags() {
        return new int[] { 0 };
    }

    public Rdrctr(PluginWrapper wrapper) {
        super(wrapper);
    }

    // @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        String declink;
        // Workaround for preview.tinyurl.com Links
        parameter = parameter.replaceFirst("preview\\.tinyurl\\.com", "tinyurl\\.com");

        // Workaround for ponyurl.com Links
        parameter = parameter.replace("ponyurl.com/", "ponyurl.com/forward.php?");
        br.getPage(parameter);
        String redirectcheck = br.getRedirectLocation();
        String declink2 = null;

        // Password input for skracaj.org/l-x.pl redirector
        if (br.containsHTML("<b>Podaj has.?o dost.?pu:</b><br />")) {
            Form passForm = br.getForm(0);
            String password = UserIO.getInstance().requestInputDialog("Enter password for redirect link:");
            passForm.put("shortcut_password", password);
            br.submitForm(passForm);
        }
        if (parameter.contains("adf.ly/") && redirectcheck == null && br.containsHTML("\"frame1\"")) {
            String framelink = br.getRegex("\"frame1\" src=\"(/.*?)\"").getMatch(0);
            if (framelink == null) return null;
            framelink = "http://adf.ly" + framelink;
            br.getPage(framelink);
            declink2 = br.getRegex("\"skip_button\" href=\"(.*?)\"").getMatch(0);
        }

        declink = redirectcheck;
        if (declink == null) declink = br.getRegex("<iframe frameborder=\"0\"  src=\"(.*?)\"").getMatch(0);
        if (declink == null) {
            declink = declink2;
        }
        if (declink == null) return null;
        decryptedLinks.add(createDownloadlink(declink));
        return decryptedLinks;
    }

    // @Override

}
