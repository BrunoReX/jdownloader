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
import java.util.LinkedHashMap;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.DummyScriptEnginePlugin;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "drawcrowd.com" }, urls = { "http://(?:www\\.)?drawcrowd\\.com/(?!projects/)[^/]+" }, flags = { 0 })
public class DrawcrowdCom extends PluginForDecrypt {

    public DrawcrowdCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br = new Browser();
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        final String username = parameter.substring(parameter.lastIndexOf("/"));
        jd.plugins.hoster.DrawcrowdCom.setHeaders(br);
        final Browser org = br.cloneBrowser();
        br.getPage("/users" + username);
        LinkedHashMap<String, Object> json = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
        json = (LinkedHashMap<String, Object>) json.get("user");
        final String full_name = (String) json.get("full_name");
        final short entries_per_page = 200;
        int entries_total = (int) DummyScriptEnginePlugin.toLong(json.get("project_count"), 0);
        int offset = 0;

        final FilePackage fp = FilePackage.getInstance();
        fp.setName(full_name);

        do {
            br = org.cloneBrowser();
            br.getPage(username + "/projects?sort=newest&offset=" + offset + "&limit=" + entries_per_page);
            json = (LinkedHashMap<String, Object>) jd.plugins.hoster.DummyScriptEnginePlugin.jsonToJavaObject(br.toString());
            final ArrayList<Object> ressourcelist = (ArrayList) json.get("projects");
            for (final Object resource : ressourcelist) {
                json = (LinkedHashMap<String, Object>) resource;
                final String id = (String) json.get("slug");
                final String description = (String) json.get("description");
                if (inValidate(id)) {
                    return null;
                }
                final String url_content = "http://drawcrowd.com/projects/" + id;
                final DownloadLink dl = createDownloadlink(url_content);
                dl.setContentUrl(url_content);
                if (!inValidate(description)) {
                    dl.setComment(description);
                }
                dl.setLinkID(id);
                dl.setName(full_name + "_" + id + ".jpg");
                dl.setProperty("full_name", full_name);
                dl.setAvailable(true);
                fp.add(dl);
                decryptedLinks.add(dl);
                distribute(dl);
                offset++;
            }

            if (ressourcelist.size() < entries_per_page) {
                /* Fail safe */
                break;
            }

        } while (decryptedLinks.size() < entries_total);

        return decryptedLinks;
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     */
    public static boolean inValidate(final String s) {
        if (s == null || s.matches("\\s+") || s.equals("")) {
            return true;
        } else {
            return false;
        }
    }
}
