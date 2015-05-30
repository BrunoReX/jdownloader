//    jDownloader - Downloadmanager
//    Copyright (C) 2011  JD-Team support@jdownloader.org
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
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;

//Similar to SafeUrlMe (safeurl.me)
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "safelinking.net" }, urls = { "https?://(?:www\\.)?(safelinking\\.net/(?:(?:p|d(?:/com)?)/[a-f0-9]{10}|[a-zA-Z0-9]{7})|sflk\\.in/[a-f0-9]{10})" }, flags = { 0 })
public class SflnkgNt extends abstractSafeLinking {

    public SflnkgNt(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = super.decryptIt(param, progress);
        return decryptedLinks;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return true;
    }

    @Override
    protected boolean supportsHTTPS() {
        return true;
    }

    @Override
    protected boolean enforcesHTTPS() {
        return true;
    }

    @Override
    protected boolean useRUA() {
        return true;
    }

    @Override
    protected String regexLinkD() {
        return "https?://[^/]*" + regexSupportedDomains() + "/d(?:/com)?/[a-z0-9]+";
    }

}