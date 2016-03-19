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
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "pan.baidu.com" }, urls = { "http://(www\\.)?(?:pan|yun)\\.baidu\\.com/(share|wap)/[a-z\\?\\&]+(shareid|uk)=\\d+\\&(shareid|uk)=\\d+(\\&fid=\\d+)?(.*?\\&dir.+)?|https?://(www\\.)?pan\\.baidu\\.com/s/[A-Za-z0-9]+" }, flags = { 0 })
public class PanBaiduCom extends PluginForDecrypt {

    public PanBaiduCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static boolean      pluginloaded                          = false;
    private static final String TYPE_FOLDER_SUBFOLDER                 = "http://(www\\.)?pan\\.baidu\\.com/share/[a-z\\?\\&]+(shareid|uk)=\\d+\\&(uk|shareid)=\\d+(?:.*?&dir=.+|#dir/path=%2F.+)";
    private static final String TYPE_FOLDER_GENERAL                   = "http://(www\\.)?pan\\.baidu\\.com/share/[a-z\\?\\&]+((shareid|uk)=\\d+\\&(shareid|uk)=\\d+(.*?&dir=.+|#dir/path=%2F.+))";
    private static final String TYPE_FOLDER_NORMAL                    = "http://(www\\.)?pan\\.baidu\\.com/share/[a-z\\?\\&]+(shareid|uk)=\\d+\\&(uk|shareid)=\\d+";
    private static final String TYPE_FOLDER_NORMAL_PASSWORD_PROTECTED = "http://(www\\.)?pan\\.baidu\\.com/share/init\\?(shareid|uk)=\\d+\\&(uk|shareid)=\\d+";
    private static final String TYPE_FOLDER_SHORT                     = "http://(www\\.)?pan\\.baidu\\.com/s/[A-Za-z0-9]+";
    private static final String APPID                                 = "250528";

    private String              link_password                         = null;
    private String              link_password_cookie                  = null;
    private String              shareid                               = null;
    private String              uk                                    = null;

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString().replaceAll("(pan|yun)\\.baidu\\.com/", "pan.baidu.com/").replace("/wap/", "/share/");
        if (!parameter.matches(TYPE_FOLDER_NORMAL_PASSWORD_PROTECTED) && !parameter.matches(TYPE_FOLDER_SHORT)) {
            /* Correct invalid "view" linktypes - we need one general linkformat! */
            final String replace_part = new Regex(parameter, "(baidu\\.com/share/[a-z]+)").getMatch(0);
            if (replace_part != null) {
                parameter = parameter.replaceAll(replace_part, "baidu.com/share/link");
            }
        }
        br.setFollowRedirects(true);
        this.br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:41.0) Gecko/20100101 Firefox/41.0");
        /* If we access urls without cookies we might get 404 responses for no reason so let's access the main page first. */
        this.br.getPage("http://pan.baidu.com");
        this.br.getPage(parameter);
        if (br.getURL().contains("/error") || br.containsHTML("id=\"share_nofound_des\"")) {
            logger.info("Link offline: " + parameter);
            final DownloadLink dl = createDownloadlink("http://pan.baidudecrypted.com/" + System.currentTimeMillis() + new Random().nextInt(10000));
            dl.setContentUrl(parameter);
            dl.setProperty("offline", true);
            dl.setFinalFileName(new Regex(parameter, "pan\\.baidu\\.com/(.+)").getMatch(0));
            decryptedLinks.add(dl);
            return decryptedLinks;
        }

        uk = br.getRegex("yunData\\.MYUK = \"(\\d+)\"").getMatch(0);
        shareid = br.getRegex("yunData.SHARE_ID = \"(\\d+)\";").getMatch(0);
        JDUtilities.getPluginForHost("pan.baidu.com");

        if (br.getURL().contains("/share/init")) {
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            if (parameter.matches(TYPE_FOLDER_GENERAL)) {
                uk = new Regex(parameter, "uk=(\\d+)").getMatch(0);
                shareid = new Regex(parameter, "shareid=(\\d+)").getMatch(0);
            } else {
                uk = new Regex(br.getURL(), "uk=(\\d+)").getMatch(0);
                shareid = new Regex(br.getURL(), "shareid=(\\d+)").getMatch(0);
            }
            final String linkpart = new Regex(parameter, "(pan\\.baidu\\.com/.+)").getMatch(0);
            for (int i = 1; i <= 3; i++) {
                link_password = getUserInput("Password for " + linkpart + "?", param);
                br.postPage("http://pan.baidu.com/share/verify?" + "channel=chunlei&clienttype=0&web=1&shareid=" + shareid + "&uk=" + uk + "&t=" + System.currentTimeMillis(), "vcode=&pwd=" + Encoding.urlEncode(link_password));
                if (!br.containsHTML("\"errno\":0")) {
                    continue;
                }
                break;
            }
            if (!br.containsHTML("\"errno\":0")) {
                throw new DecrypterException(DecrypterException.PASSWORD);
            }
            parameter = "http://pan.baidu.com/share/link?shareid=" + shareid + "&uk=" + uk;
            link_password_cookie = br.getCookie("http://pan.baidu.com/", "BDCLND");
            br.getHeaders().remove("X-Requested-With");
            br.getPage(parameter);
            if (br.getURL().contains("/error") || br.containsHTML("id=\"share_nofound_des\"")) {
                logger.info("Link offline: " + parameter);
                final DownloadLink dl = createDownloadlink("http://pan.baidudecrypted.com/" + System.currentTimeMillis() + new Random().nextInt(10000));
                try {
                    dl.setContentUrl(parameter);
                } catch (Throwable e) {
                }
                dl.setProperty("offline", true);
                dl.setFinalFileName(new Regex(parameter, "pan\\.baidu\\.com/(.+)").getMatch(0));
                decryptedLinks.add(dl);
                return decryptedLinks;
            }
        }

        String singleFolder = new Regex(parameter, "#dir/path=(.*?)$").getMatch(0);
        if (singleFolder == null) {
            singleFolder = new Regex(parameter, "&dir=([^&\\?]+)").getMatch(0);
        }
        String correctedBR = br.toString().replaceAll("\\\\\\\\", "\\\\").replaceAll("\\\\\"", "\"");// save unicode backslash
        String dir = null;
        // Jump into folder or get content of the main link
        if (parameter.matches(TYPE_FOLDER_SUBFOLDER)) {
            final String dirName = new Regex(parameter, "(?:dir/path=|&dir=)%2F([^&\\?]+)").getMatch(0);
            dir = "%2F" + dirName;
            getDownloadLinks(decryptedLinks, parameter, dirName, dir);
        } else if (br.containsHTML("class=\"size\"|class=\"video\\-save\\-btn\"")) {
            final String fsid = br.getRegex("yunData\\.FS_ID = \"(\\d+)\"").getMatch(0);
            if (fsid == null || uk == null || shareid == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            final DownloadLink fina = generateDownloadLink(null, parameter, null, fsid, null);
            final String filename = br.getRegex("\"server_filename\":\"([^<>\"]*?)\"").getMatch(0);
            String filesize = br.getRegex("\"size\"\\s*:\\s*\"?(\\d+)").getMatch(0);
            if (filename == null || filesize == null) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            fina.setContentUrl(parameter);
            if (parameter.contains("fid=")) {
                fina.setContainerUrl(parameter.replaceAll("\\&fid=\\d+", ""));

            }
            fina.setProperty("important_fsid", fsid);
            fina.setProperty("origurl_uk", uk);
            fina.setProperty("origurl_shareid", shareid);
            fina.setFinalFileName(Encoding.htmlDecode(unescape(filename)));
            fina.setProperty("server_filename", filename);
            fina.setDownloadSize(Long.parseLong(filesize));
            fina.setProperty("size", filesize);
            fina.setAvailable(true);
            decryptedLinks.add(fina);
        } else {
            /* create HashMap with json key/value pair */
            final String json = new Regex(correctedBR, "\"list\":\\[(\\{.*?\\})\\]\\},").getMatch(0);
            // cleanup, poor mans method to remove entries that breaks the important 'dlink'
            if (json == null) {
                logger.warning("Problemo! Please report to JDownloader Development Team, link: " + parameter);
                return null;
            }

            HashMap<String, String> ret = new HashMap<String, String>();
            for (String[] values : new Regex((json == null ? "" : json), "\\{([^\\}]+)").getMatches()) {

                for (String[] value : new Regex(values[0] + ",", "\"(.*?)\":\"?(.*?)\"?,").getMatches()) {
                    ret.put(value[0], value[1]);
                }

                if (!(ret.containsKey("headurl") || ret.containsKey("parent_path"))) {
                    continue;
                }
                if (ret.get("isdir") != null && ret.get("isdir").equals("1")) {
                    // subfolder in imported root
                    getDownloadLinks(decryptedLinks, parameter, ret.get("path"), Encoding.urlEncode(unescape(ret.get("path"))));
                } else {
                    dir = (new Regex(ret.get("headurl"), "filename=(.*?)$").getMatch(0));

                    if (dir == null) {
                        dir = (ret.get("server_filename"));
                    }
                    dir = unescape(dir);
                    // why do this exactly ?? for folder code down lower it fked this up seriously. I'm of the opinion that you should never
                    // nuke original ret storables.
                    // ret.put("path", dir);
                    // dir = ret.get("parent_path") + "%2F" + dir;

                    dir = ret.get("parent_path") + "%2F" + dir;
                    if (singleFolder != null && !singleFolder.equals(dir)) {
                        continue;// only selected folder
                    }
                    if (ret.containsKey("md5") && !"".equals(ret.get("md5"))) {// file in root
                        final DownloadLink dl = generateDownloadLink(ret, parameter, dir, null, values[0]);
                        decryptedLinks.add(dl);
                    }
                }
            }
            if (decryptedLinks.size() == 0) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
        }

        return decryptedLinks;
    }

    private void getDownloadLinks(final ArrayList<DownloadLink> decryptedLinks, final String parameter, final String dirName, final String dir) throws Exception {
        if (dirName == null || dir == null) {
            return;
        }
        br.getHeaders().put("Accept", "Accept");
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(Encoding.htmlDecode(unescape(dirName)));
        int currentpage = 1;
        final int maxpages = 10;
        final int maxlinksperpage = 100;
        int currentlinksnum = 0;
        do {
            br.getPage(getFolder(parameter, dir, currentpage));
            // Folder empty
            if (br.containsHTML("\"errno\":2")) {
                final DownloadLink dl = createDownloadlink("http://pan.baidudecrypted.com/" + System.currentTimeMillis() + new Random().nextInt(10000));
                dl.setContentUrl(parameter);
                dl.setProperty("offline", true);
                dl.setFinalFileName(Encoding.htmlDecode(dirName));
                decryptedLinks.add(dl);
                return;
            }
            HashMap<String, String> ret = null;
            String list = br.getRegex("\"list\":\\[(\\{.*?\\})\\]").getMatch(0);
            final String[][] linkInfo = new Regex((list == null ? "" : list), "\\{(.*?)\\}").getMatches();
            if (linkInfo != null && linkInfo.length != 0) {
                currentlinksnum = linkInfo.length;
                for (final String[] links : linkInfo) {
                    ret = new HashMap<String, String>();
                    for (String[] link : new Regex(links[0] + ",", "\"(.*?)\":\"?(.*?)\"?,").getMatches()) {
                        ret.put(link[0], link[1]);
                    }
                    // nested folder.. we should really return back into itself to make this process threaded.
                    if (ret.get("isdir") != null && ret.get("isdir").equals("1")) {
                        getDownloadLinks(decryptedLinks, parameter, ret.get("path"), Encoding.urlEncode(unescape(ret.get("path"))));
                    }
                    if (links[0].contains("\"md5\"")) {
                        final DownloadLink dl = generateDownloadLink(ret, parameter, dir, null, null);
                        dl.setContainerUrl(parameter);
                        fp.add(dl);
                        distribute(dl);
                        decryptedLinks.add(dl);
                    }
                }
            }
            currentpage++;
        } while (currentlinksnum >= maxlinksperpage && currentpage <= maxpages);

    }

    private String getFolder(final String parameter, String dir, final int page) {
        String unicode_stuff = new Regex(dir, "_(.+)$").getMatch(0);
        if (unicode_stuff != null) {
            dir = dir.replace(unicode_stuff, "");
            unicode_stuff = unescape(unicode_stuff);
            dir += Encoding.urlEncode(unicode_stuff);
        }
        if (shareid == null) {
            shareid = new Regex(parameter, "shareid=(\\d+)").getMatch(0);
        }
        if (shareid == null) {
            shareid = br.getRegex("\"shareid\":(\\d+)").getMatch(0);
        }
        if (shareid == null) {
            shareid = new Regex(parameter, "/s/(.*)").getMatch(0);
        }
        if (uk == null) {
            uk = new Regex(parameter, "uk=(\\d+)").getMatch(0);
        }
        if (uk == null) {
            uk = br.getRegex("uk=(\\d+)").getMatch(0);
        }
        return "http://pan.baidu.com/share/list?uk=" + (uk != null ? uk : "") + "&shareid=" + (shareid != null ? shareid : "") + "&page=" + page + "&num=100&dir=" + dir + "&order=time&desc=1&_=" + System.currentTimeMillis() + "&bdstoken=&channel=chunlei&clienttype=0&web=1&app_id=" + APPID;
    }

    private static synchronized String unescape(final String s) {
        /* we have to make sure the youtube plugin is loaded */
        if (pluginloaded == false) {
            final PluginForHost plugin = JDUtilities.getPluginForHost("youtube.com");
            if (plugin == null) {
                throw new IllegalStateException("youtube plugin not found!");
            }
            pluginloaded = true;
        }
        return jd.nutils.encoding.Encoding.unescapeYoutube(s);
    }

    /** @description: Differ between subfolders & downloadlinks and add them */
    private DownloadLink generateDownloadLink(final HashMap<String, String> ret, final String parameter, final String dir, String fsid, final String json) {
        DownloadLink dl = null;
        String isdir = "0";
        if (ret != null) {
            isdir = ret.get("isdir");
        }
        if ("1".equals(isdir)) {
            String subdir_name = ret.get("server_filename");
            subdir_name = Encoding.urlEncode(unescape(subdir_name));
            String subdir_link = null;
            if (parameter.matches(TYPE_FOLDER_SUBFOLDER)) {
                subdir_link = parameter + "%2F" + subdir_name;
            } else {
                subdir_link = parameter + "#dir/path=%2F" + subdir_name;
            }
            dl = createDownloadlink(subdir_link);
        } else {
            dl = createDownloadlink("http://pan.baidudecrypted.com/" + System.currentTimeMillis() + new Random().nextInt(10000));

            if (fsid == null) {
                fsid = ret.get("fs_id");
            }

            if (ret != null) {
                for (Entry<String, String> next : ret.entrySet()) {
                    dl.setProperty(next.getKey(), next.getValue());
                }
            }

            /* Workaround for bad code before */
            String filename = null;
            if (json != null) {
                filename = new Regex(json, "\"server_filename\":\"([^<>\"]*?)\"").getMatch(0);
            }
            if (filename == null) {
                filename = dl.getStringProperty("server_filename");
            }
            filename = Encoding.htmlDecode(unescape(filename));
            dl.setProperty("server_filename", filename);

            if (dl.getStringProperty("server_filename") != null) {
                dl.setFinalFileName(Encoding.htmlDecode(unescape(dl.getStringProperty("server_filename"))));
            }
            if (dl.getStringProperty("size") != null) {
                dl.setDownloadSize(Long.parseLong(dl.getStringProperty("size")));
            }
            dl.setProperty("mainLink", getPlainLink(parameter));
            dl.setProperty("dirName", dir);
            dl.setProperty("important_link_password", link_password);
            dl.setProperty("important_link_password_cookie", link_password_cookie);
            dl.setProperty("important_fsid", fsid);

            dl.setContentUrl(parameter + "&fid=" + fsid);
            dl.setProperty("origurl_uk", uk);
            dl.setProperty("origurl_shareid", shareid);
            dl.setAvailable(true);
        }
        return dl;
    }

    private String getPlainLink(final String input) {
        return input.replace("/share/init?", "/share/link?");
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }

}