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

package org.jdownloader.extensions.extraction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Pattern;

import org.appwork.utils.Application;
import org.appwork.utils.Regex;
import org.jdownloader.logging.LogController;

public class FileSignatures {

    private final Signature      SIG_TXT = new Signature("TXTfile", null, "Plaintext", ".*\\.(txt|doc|nfo|html|htm|xml)");
    private volatile Signature[] SIGNATURES;

    public static String readFileSignature(File f) throws IOException {
        return readFileSignature(f, 10);
    }

    public static String readFileSignature(final File f, final int length) throws IOException {
        FileInputStream reader = null;
        try {
            final StringBuilder sig = new StringBuilder();
            if (length > 0 && f.exists()) {
                reader = new FileInputStream(f);
                for (int i = 0; i < length; i++) {
                    final int h = reader.read();
                    if (h != -1) {
                        final String s = Integer.toHexString(h);
                        if (s.length() < 2) {
                            sig.append('0');
                        }
                        sig.append(s);
                    } else {
                        break;
                    }
                }
            }
            return sig.toString();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (final Throwable e) {
            }
        }
    }

    /**
     * Gibt alle verfügbaren signaturen zurück
     *
     * @return
     */
    private Signature[] getSignatureList() {
        if (SIGNATURES != null) {
            return SIGNATURES;
        }
        synchronized (this) {
            if (SIGNATURES != null) {
                return SIGNATURES;
            }
            String[] m;
            try {
                m = Regex.getLines(org.appwork.utils.IO.readURLToString(Application.getRessourceURL("org/jdownloader/extensions/extraction/mime.type")));
            } catch (IOException e1) {
                LogController.CL().log(e1);
                return new Signature[0];
            }
            Signature[] ret = new Signature[m.length];
            int i = 0;
            for (String e : m) {
                String[] entry = e.split(":::");
                if (entry.length >= 5) {
                    ret[i++] = new Signature(entry[0], entry[1], entry[2], entry[3], entry[4]);
                } else if (entry.length >= 4) {
                    ret[i++] = new Signature(entry[0], entry[1], entry[2], entry[3]);
                } else {
                    LogController.CL().warning("Signature " + e + " invalid!");
                }
            }
            SIGNATURES = ret;
        }
        return SIGNATURES;
    }

    /**
     * GIbt die signatur zu einem signaturstring zurück.
     *
     * @param sig
     * @return
     */
    public Signature getSignature(final String sig) {
        if (sig != null) {
            final Signature[] db = getSignatureList();
            for (final Signature entry : db) {
                if (entry != null && entry.matches(sig)) {
                    return entry;
                }
            }
            return checkTxt(sig);
        }
        return null;
    }

    public Signature getSignature(final String signature, final String fileName) {
        if (signature != null) {
            if (fileName == null) {
                return getSignature(signature);
            } else {
                Signature ret = null;
                final Signature[] db = getSignatureList();
                for (final Signature sig : db) {
                    if (sig != null && sig.matches(signature)) {
                        final Pattern extensionSure = sig.getExtensionSure();
                        if (extensionSure != null) {
                            if (extensionSure.matcher(fileName).matches()) {
                                return sig;
                            }
                        } else {
                            ret = sig;
                        }
                    }
                }
                if (ret == null) {
                    return checkTxt(signature);
                } else {
                    return ret;
                }
            }
        }
        return null;
    }

    /**
     * Prüft ob eine Datei möglicheriwese eine TXT datei ist. Dabei wird geprüft ob die signatur nur aus lesbaren zeichen besteht
     *
     * @param sig
     * @return
     */
    private Signature checkTxt(String sig) {
        for (int i = 0; i < sig.length(); i += 2) {
            if ((i + 2) > sig.length()) {
                return null;
            }
            String b = sig.substring(i, i + 2);
            int ch = Integer.parseInt(b, 16);

            if (ch < 32 || ch > 126) {
                return null;
            }

        }

        return SIG_TXT;
    }
}
