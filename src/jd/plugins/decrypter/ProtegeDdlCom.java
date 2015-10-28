//jDownloader - Downloadmanager
//Copyright (C) 2012  JD-Team support@jdownloader.org
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

import java.io.File;
import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.RandomUserAgent;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.hoster.DirectHTTP;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "protege-ddl.com" }, urls = { "http://(www\\.)?protege\\-ddl\\.com/(check\\.[a-z]{10}|[a-z]{10}\\-.+)\\.html" }, flags = { 0 })
public class ProtegeDdlCom extends PluginForDecrypt {

    // DEV NOTES
    // - No https

    public ProtegeDdlCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String ua = RandomUserAgent.generate();

    @SuppressWarnings("static-access")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setCookie("http://www.protege-ddl", "lang", "english");
        br.getHeaders().put("User-Agent", ua);
        br.setFollowRedirects(true);
        br.getPage(parameter);

        // error clauses
        if (br.containsHTML("(?i)>Not Found</h1>")) {
            logger.info("Invalid URL: " + parameter);
            return decryptedLinks;
        }
        // find correct forum, post form
        final Form getform = br.getFormbyProperty("name", "linkprotect");
        if (getform != null) {
            boolean failed = true;
            for (int i = 0; i <= 4; i++) {
                final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
                rc.findID();
                rc.load();
                final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                final String c = getCaptchaCode("recaptcha", cf, param);
                getform.put("recaptcha_challenge_field", rc.getChallenge());
                getform.put("recaptcha_response_field", Encoding.urlEncode(c));
                br.submitForm(getform);
                if (!br.containsHTML("(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)")) {
                    failed = false;
                    break;
                }
            }
            if (failed) {
                throw new DecrypterException(DecrypterException.CAPTCHA);
            }
        }

        // find tables
        String table = br.getRegex("<table(.*?)</table>").getMatch(0);
        // find links
        String[] links = new Regex(table, "(?i)<a href=(.*?)[\r\n\t ]+").getColumn(0);
        if (links == null || links.length == 0) {
            links = new Regex(table, "(?i)blank>(.*?)</a>").getColumn(0);
            if (links == null || links.length == 0) {
                if (br.containsHTML("<h4>Password:</h4>")) {
                    logger.info("Password protected links are not supported yet: " + parameter);
                    return decryptedLinks;
                }
                logger.warning("ProtectUp: Either invalid URL or the plugin broken : " + parameter);
                logger.warning("ProtectUp: Please confirm via browser, and report any bugs to developement team.");
                return null;
            }
        }
        if (links != null && links.length > 0) {
            for (String link : links) {
                decryptedLinks.add(createDownloadlink(link));
            }
        }
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}