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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Pattern;

import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.HTTP;
import jd.plugins.PluginForDecrypt;
import jd.plugins.RequestInfo;

public class Bm4uin extends PluginForDecrypt {
    static private final String host = "bm4u.in";
    private static final Pattern patternSupported = Pattern.compile("http://[\\w\\.]*?bm4u\\.in/index\\.php\\?do=show_download&id=\\d+", Pattern.CASE_INSENSITIVE);

    // private String version = "1.0.0.0";

    public Bm4uin() {
        super();
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(String parameter) {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        try {
            URL url = new URL(parameter);
            RequestInfo requestInfo = HTTP.getRequest(url);
            String pass = requestInfo.getFirstMatch("<strong>Password:</strong> <b><font color=red>(.*?)</font></b>");
            String[][] links = requestInfo.getRegexp("onClick=\"window\\.open\\('crypt\\.php\\?id=([\\d]+)&amp;mirror=([\\d\\w]+)&part=([\\d]+)").getMatches();
            for (String[] element : links) {
                url = new URL("http://bm4u.in/crypt.php?id=" + element[0] + "&mirror=" + element[1] + "&part=" + element[2]);
                requestInfo = HTTP.getRequest(url);
                DownloadLink link = createDownloadlink(requestInfo.getFirstMatch("<iframe src=\"(.*?)\" width").trim());
                link.addSourcePluginPassword(pass);
                decryptedLinks.add(link);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return decryptedLinks;
    }

    @Override
    public boolean doBotCheck(File file) {
        return false;

    }

    @Override
    public String getCoder() {
        return "JD-Team";
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getPluginName() {
        return host;
    }

    @Override
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    @Override
    public String getVersion() {
        String ret = new Regex("$Revision$", "\\$Revision: ([\\d]*?) \\$").getFirstMatch();
        return ret == null ? "0.0" : ret;
    }
}
