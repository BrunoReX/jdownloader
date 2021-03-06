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
import jd.config.SubConfiguration;
import jd.controlling.ProgressController;
import jd.http.Browser.BrowserException;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "sta.sh" }, urls = { "http://(www\\.)?sta\\.sh/[a-z0-9]+" }) 
public class StaShDecrypter extends PluginForDecrypt {

    public StaShDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private final String  INVALIDLINKS           = "http://(www\\.)?sta\\.sh/(muro|writer|login)";
    private static String FORCEHTMLDOWNLOAD      = "FORCEHTMLDOWNLOAD";
    private static String USE_LINKID_AS_FILENAME = "USE_LINKID_AS_FILENAME";
    private static String DOWNLOAD_ZIP           = "DOWNLOAD_ZIP";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        JDUtilities.getPluginForHost("sta.sh");
        final SubConfiguration cfg = SubConfiguration.getConfig("sta.sh");
        final boolean force_html_dl = cfg.getBooleanProperty(FORCEHTMLDOWNLOAD, false);
        final boolean linkid_as_filename = cfg.getBooleanProperty(USE_LINKID_AS_FILENAME, false);
        final String main_linkid = new Regex(parameter, "sta\\.sh/(.+)").getMatch(0);
        final DownloadLink main = createDownloadlink(parameter.replace("sta.sh/", "stadecrypted.sh/"));
        if (parameter.matches(INVALIDLINKS)) {
            main.setAvailable(false);
            decryptedLinks.add(main);
            return decryptedLinks;
        }
        try {
            br.getPage(parameter);
        } catch (final BrowserException e) {
            main.setAvailable(false);
            decryptedLinks.add(main);
            return decryptedLinks;
        }
        final String[][] picdata = br.getRegex("class=\"thumb\" href=\"(https?://(www\\.)?sta\\.sh/[a-z0-9]+)\" title=\"([^<>\"]*?)\"").getMatches();
        if (picdata == null || picdata.length == 0) {
            decryptedLinks.add(main);
            return decryptedLinks;
        }

        String fpName = br.getRegex("name=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
        if (fpName == null) {
            fpName = new Regex(parameter, "([a-z0-9]+)$").getMatch(0);
        }
        fpName = Encoding.htmlDecode(fpName.trim());

        for (final String singleLinkData[] : picdata) {
            final String url = singleLinkData[0];
            final String linkid = new Regex(url, "sta\\.sh/(.+)").getMatch(0);
            String name = Encoding.htmlDecode(singleLinkData[2]);
            final DownloadLink dl = createDownloadlink(url.replace("sta.sh/", "stadecrypted.sh/"));
            /* Obey user setting */
            if (linkid_as_filename) {
                name = linkid;
            }
            if (force_html_dl) {
                dl.setName(name + ".html");
                dl.setAvailable(true);
            } else {
                /* No ext found --> Don't set available, let host plugin perform a full check to find the correct name */
                final String ext = jd.plugins.hoster.StaSh.getFileExt(this.br);
                if (ext != null) {
                    dl.setName(name + "." + ext);
                    dl.setAvailable(true);
                } else {
                    dl.setName(name);
                }
            }
            decryptedLinks.add(dl);
        }
        final String zipLink = br.getRegex("\"(/zip/[a-z0-9]+)\"").getMatch(0);
        if (cfg.getBooleanProperty(DOWNLOAD_ZIP, false) && zipLink != null) {
            final DownloadLink zip = createDownloadlink(parameter.replace("sta.sh/", "stadecrypted.sh/"));
            zip.setProperty("iszip", true);
            zip.setProperty("directlink", zipLink);
            String zip_filename;
            if (linkid_as_filename) {
                zip_filename = main_linkid;
            } else {
                zip_filename = fpName;
            }
            if (force_html_dl) {
                zip.setName(zip_filename + ".html");
            } else {
                zip.setName(zip_filename + ".zip");
            }
            zip.setAvailable(true);
            decryptedLinks.add(zip);
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);
        fp.addLinks(decryptedLinks);
        return decryptedLinks;
    }
}
