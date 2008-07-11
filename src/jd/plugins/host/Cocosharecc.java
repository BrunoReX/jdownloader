package jd.plugins.host;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import jd.config.Configuration;
import jd.plugins.DownloadLink;
import jd.plugins.HTTP;
import jd.plugins.HTTPConnection;
import jd.plugins.PluginForHost;
import jd.plugins.PluginStep;
import jd.plugins.RequestInfo;
import jd.plugins.download.RAFDownload;
import jd.utils.JDUtilities;

public class Cocosharecc extends PluginForHost {
    private static final String CODER = "JD-Team";

    private static final String HOST = "cocoshare.cc";

    private static final String PLUGIN_NAME = HOST;

    private static final String PLUGIN_VERSION = "1.0.0.0";

    private static final String PLUGIN_ID = PLUGIN_NAME + "-" + PLUGIN_VERSION;

    static private final Pattern PAT_SUPPORTED = Pattern.compile("http://[\\w\\.]*?cocoshare\\.cc/\\d+/(.*)", Pattern.CASE_INSENSITIVE);
    private RequestInfo requestInfo;
    private String downloadurl;

    public Cocosharecc() {
        super();
        steps.add(new PluginStep(PluginStep.STEP_COMPLETE, null));
    }

    @Override
    public boolean doBotCheck(File file) {
        return false;
    }

    @Override
    public String getCoder() {
        return CODER;
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public String getHost() {
        return HOST;
    }

    @Override
    public String getVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public String getPluginID() {
        return PLUGIN_ID;
    }

    @Override
    public Pattern getSupportedLinks() {
        return PAT_SUPPORTED;
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanDownloadNum() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean getFileInformation(DownloadLink downloadLink) {
        try {
            downloadurl = downloadLink.getDownloadURL();
            requestInfo = HTTP.getRequest(new URL(downloadurl));
            if (requestInfo.containsHTML("Download startet automatisch")) {
                String filename = requestInfo.getRegexp("<h1>(.*?)</h1>").getFirstMatch();
                String filesize;
                if ((filesize = requestInfo.getRegexp("Dateigr&ouml;sse:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(.*?)Bytes<br").getFirstMatch()) != null) {                    
                    downloadLink.setDownloadMax(new Integer(filesize.trim().replaceAll("\\.", "")));
                }
                downloadLink.setName(filename);
                return true;
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        downloadLink.setAvailable(false);
        return false;
    }

    public PluginStep doStep(PluginStep step, DownloadLink downloadLink) {
        if (step == null) return null;
        try {
            /* Nochmals das File überprüfen */
            if (!getFileInformation(downloadLink)) {
                downloadLink.setStatus(DownloadLink.STATUS_ERROR_FILE_NOT_FOUND);
                step.setStatus(PluginStep.STATUS_ERROR);
                return step;
            }

            /*Warten*/
            String waittime=requestInfo.getRegexp("var num_timeout = (\\d+);").getFirstMatch();
            if (waittime!=null){
                this.sleep(new Integer(waittime.trim())*1000, downloadLink);    
            }
            
            /* DownloadLink holen */            
            downloadurl="http://www.cocoshare.cc"+requestInfo.getRegexp("<meta http-equiv=\"refresh\" content=\"\\d+; URL=(.*?)\"").getFirstMatch();
            requestInfo = HTTP.getRequestWithoutHtmlCode(new URL(downloadurl), null, downloadLink.getDownloadURL(), false);
            downloadurl= requestInfo.getLocation();
            if (downloadurl==null) {
                downloadLink.setStatus(DownloadLink.STATUS_ERROR_FILE_NOT_FOUND);
                step.setStatus(PluginStep.STATUS_ERROR);
                return step;
            }
            downloadurl="http://www.cocoshare.cc"+downloadurl;
            requestInfo = HTTP.getRequestWithoutHtmlCode(new URL(downloadurl), null, downloadLink.getDownloadURL(), false);
            
            /* DownloadLimit? */
            if (requestInfo.getLocation() != null) {
                step.setStatus(PluginStep.STATUS_ERROR);
                step.setParameter(120000L);
                downloadLink.setStatus(DownloadLink.STATUS_ERROR_STATIC_WAITTIME);
                return step;
            }

            /* Datei herunterladen */
            HTTPConnection urlConnection = requestInfo.getConnection();
            String filename = getFileNameFormHeader(urlConnection);
            if (urlConnection.getContentLength() == 0) {
                downloadLink.setStatus(DownloadLink.STATUS_ERROR_TEMPORARILY_UNAVAILABLE);
                step.setStatus(PluginStep.STATUS_ERROR);
                return step;
            }
            downloadLink.setDownloadMax(urlConnection.getContentLength());
            downloadLink.setName(filename);
            long length = downloadLink.getDownloadMax();
            dl = new RAFDownload(this, downloadLink, urlConnection);
            dl.setFilesize(length);
            dl.setChunkNum(JDUtilities.getSubConfig("DOWNLOAD").getIntegerProperty(Configuration.PARAM_DOWNLOAD_MAX_CHUNKS, 2));
            dl.setResume(true);
            if (!dl.startDownload() && step.getStatus() != PluginStep.STATUS_ERROR && step.getStatus() != PluginStep.STATUS_TODO) {
                downloadLink.setStatus(DownloadLink.STATUS_ERROR_TEMPORARILY_UNAVAILABLE);
                step.setStatus(PluginStep.STATUS_ERROR);
                return step;
            }
            return step;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        step.setStatus(PluginStep.STATUS_ERROR);
        downloadLink.setStatus(DownloadLink.STATUS_ERROR_UNKNOWN);
        return step;
    }

    @Override
    public void resetPluginGlobals() {
        // TODO Auto-generated method stub
    }

    @Override
    public String getAGBLink() {
        return "http://www.cocoshare.cc/imprint";
    }
}
