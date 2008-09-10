//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
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

package jd.plugins.decrypt;

import java.util.ArrayList;
import java.util.regex.Pattern;

import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

public class Bm4uin extends PluginForDecrypt {
    public Bm4uin(String cfgName) {
        super(cfgName);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();

        String page = br.getPage(parameter);
        String pass = new Regex(page, Pattern.compile("<strong>Password:</strong> <b><font color=red>(.*?)</font></b>", Pattern.CASE_INSENSITIVE)).getMatch(0);
        String[][] links = new Regex(page, Pattern.compile("onClick=\"window\\.open\\('crypt\\.php\\?id=([\\d]+)&amp;mirror=([\\d\\w]+)&part=([\\d]+)", Pattern.CASE_INSENSITIVE)).getMatches();
        progress.setRange(links.length);
        for (String[] element : links) {
            DownloadLink link = createDownloadlink(new Regex(br.getPage("http://bm4u.in/crypt.php?id=" + element[0] + "&mirror=" + element[1] + "&part=" + element[2]), Pattern.compile("<iframe src=\"(.*?)\" width", Pattern.CASE_INSENSITIVE)).getMatch(0).trim());
            link.addSourcePluginPassword(pass);
            decryptedLinks.add(link);
            progress.increase(1);
        }

        return decryptedLinks;
    }

    @Override
    public String getCoder() {
        return "JD-Team";
    }

    @Override
    public String getVersion() {
        String ret = new Regex("$Revision$", "\\$Revision: ([\\d]*?) \\$").getMatch(0);
        return ret == null ? "0.0" : ret;
    }
}
