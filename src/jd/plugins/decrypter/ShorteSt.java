//jDownloader - Downloadmanager
//Copyright (C) 2014  JD-Team support@jdownloader.org
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
import java.util.Random;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "shorte.st" }, urls = { "http://(www\\.)?sh\\.st/[^<>\r\n\t]+" }, flags = { 0 })
public class ShorteSt extends antiDDoSForDecrypt {

    public ShorteSt(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.getPage(parameter);
        final String redirect = br.getRegex("<meta http-equiv=\"refresh\" content=\"\\d+\\;url=(.*?)\" \\/>").getMatch(0);
        if (redirect != null && redirect.contains("sh.st/login")) {
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        } else if (redirect != null) {
            parameter = redirect;
            br.getPage(parameter);
        }
        if (br.containsHTML(">page not found<")) {
            logger.info("Link offline: " + parameter);
            decryptedLinks.add(createOfflinelink(parameter));
            return decryptedLinks;
        }

        final String timer = getJson("seconds");
        final String cb = getJson("callbackUrl");
        final String sid = getJson("sessionId");
        if (cb == null || sid == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        int t = 5;
        if (timer != null) {
            t = Integer.parseInt(timer);
        }
        sleep(t * 1001, param);
        final Browser br2 = br.cloneBrowser();
        br2.getHeaders().put("Accept", "application/json, text/javascript");
        br2.getHeaders().put("Content-Type", "application/x-www-form-urlencoded");
        br2.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br2.postPage(cb, "adSessionId=" + sid + "&callback=reqwest_" + new Regex(String.valueOf(new Random().nextLong()), "(\\d{10})$").getMatch(0));
        final String finallink = getJson(br2, "destinationUrl");
        if (finallink == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        decryptedLinks.add(createDownloadlink(finallink));
        return decryptedLinks;
    }

    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}
