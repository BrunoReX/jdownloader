package org.jdownloader.captcha.v2.solver.browser;

import java.io.IOException;

import org.appwork.exceptions.WTFException;
import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.net.protocol.http.HTTPConstants.ResponseCode;
import org.appwork.remoteapi.exceptions.BasicRemoteAPIException;
import org.appwork.utils.Exceptions;
import org.appwork.utils.Hash;
import org.appwork.utils.StringUtils;
import org.appwork.utils.net.HTTPHeader;
import org.appwork.utils.net.httpserver.HttpHandlerInfo;
import org.appwork.utils.net.httpserver.handler.HttpRequestHandler;
import org.appwork.utils.net.httpserver.requests.GetRequest;
import org.appwork.utils.net.httpserver.requests.PostRequest;
import org.appwork.utils.net.httpserver.responses.HttpResponse;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.processes.ProcessBuilderFactory;
import org.jdownloader.api.DeprecatedAPIHttpServerController;
import org.jdownloader.captcha.v2.solver.service.BrowserSolverService;
import org.jdownloader.controlling.UniqueAlltimeID;

public abstract class BrowserReference implements HttpRequestHandler {

    private HttpHandlerInfo          handlerInfo;
    private AbstractBrowserChallenge challenge;
    private UniqueAlltimeID          id;
    private int                      port;

    private Process                  process;
    private double                   scale;
    private BrowserWindow            browserWindow;
    private BrowserViewport          viewport;

    public BrowserReference(AbstractBrowserChallenge challenge) {
        this.challenge = challenge;
        id = new UniqueAlltimeID();
        // this should get setter in advanced.
        this.port = 12345;
    }

    public void open() throws IOException {
        handlerInfo = DeprecatedAPIHttpServerController.getInstance().registerRequestHandler(port, true, this);
        openURL("http://127.0.0.1:" + port + "/" + Hash.getMD5(this.challenge.getPlugin().getClass().getName()) + "?id=" + id.getID());
    }

    private void openURL(String url) {

        String[] browserCmd = BrowserSolverService.getInstance().getConfig().getBrowserCommandline();

        if (browserCmd == null || browserCmd.length == 0) {
            // if (CrossSystem.isWindows()) {
            //
            // try {
            // // Get registry where we find the default browser
            //
            // ProcessOutput result = ProcessBuilderFactory.runCommand("REG", "QUERY", "HKEY_CLASSES_ROOT\\http\\shell\\open\\command");
            // String string = result.getStdOutString("UTF-8");
            // String pathToExecutable = new Regex(string, "\"([^\"]+)").getMatch(0);
            // File file = new File(pathToExecutable);
            // if (file.exists() && file.getName().toLowerCase(Locale.ENGLISH).endsWith(".exe")) {
            // browserCmd = new String[] { file.getAbsolutePath(), "%%%url%%%" };
            // if (file.getName().toLowerCase(Locale.ENGLISH).endsWith("chrome.exe")) {
            // browserCmd = new String[] { file.getAbsolutePath(), "--app=%%%url%%%", "--window-position=%%%x%%%,%%%y%%%",
            // "--window-size=%%%width%%%,%%%height%%%" };
            // }
            //
            // }
            //
            // } catch (Exception e) {
            // e.printStackTrace();
            //
            // }
            // }
        }
        if (browserCmd == null || browserCmd.length == 0) {
            CrossSystem.openURL(url);
        } else {
            // Point pos = getPreferredBrowserPosition();
            // Dimension size = getPreferredBrowserSize();
            String[] cmds = new String[browserCmd.length];
            for (int i = 0; i < browserCmd.length; i++) {
                cmds[i] = browserCmd[i].replace("%s", url);

            }

            ProcessBuilder pb = ProcessBuilderFactory.create(cmds);
            pb.redirectErrorStream(true);
            try {
                process = pb.start();

                // String str = IO.readInputStreamToString(process.getInputStream());
                // System.out.println(str);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    //
    // private Dimension getPreferredBrowserSize() {
    // return new Dimension(300, 600);
    // }
    //
    // private Point getPreferredBrowserPosition() {
    // return new Point(0, 0);
    // }

    public void dispose() {

        if (handlerInfo != null) {
            DeprecatedAPIHttpServerController.getInstance().unregisterRequestHandler(handlerInfo);
        }

        if (process != null) {

            process.destroy();
            process = null;
        }
    }

    @Override
    public boolean onGetRequest(GetRequest request, HttpResponse response) throws BasicRemoteAPIException {

        if (!StringUtils.equals(request.getRequestedPath(), "/" + Hash.getMD5(this.challenge.getPlugin().getClass().getName()))) {
            return false;
        }

        try {
            String pDo = request.getParameterbyKey("do");
            String id = request.getParameterbyKey("id");
            if (!StringUtils.equals(id, this.id.getID() + "")) {
                return false;
            }
            response.setResponseCode(ResponseCode.SUCCESS_OK);
            response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_RESPONSE_CONTENT_TYPE, "text/html; charset=utf-8"));

            if ("loaded".equals(pDo)) {

                browserWindow = new BrowserWindow(Integer.parseInt(request.getParameterbyKey("x")), Integer.parseInt(request.getParameterbyKey("y")), Integer.parseInt(request.getParameterbyKey("w")), Integer.parseInt(request.getParameterbyKey("h")), Integer.parseInt(request.getParameterbyKey("vw")), Integer.parseInt(request.getParameterbyKey("vh")));
                if (BrowserSolverService.getInstance().getConfig().isAutoClickEnabled()) {

                    this.viewport = challenge.getBrowserViewport(browserWindow);
                    viewport.onLoaded();

                    response.getOutputStream(true).write("ok".getBytes("UTF-8"));

                }
                return true;
            } else if ("canClose".equals(pDo)) {
                response.getOutputStream(true).write("false".getBytes("UTF-8"));
            } else if (pDo == null) {

                response.getOutputStream(true).write(challenge.getHTML().getBytes("UTF-8"));
            } else {
                return challenge.onGetRequest(this, request, response);

            }
            return true;
        } catch (Throwable e) {
            error(response, e);
            return true;
        }

    }

    private void error(HttpResponse response, Throwable e) {
        try {
            response.setResponseCode(ResponseCode.SERVERERROR_INTERNAL);
            response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_RESPONSE_CONTENT_TYPE, "text/html; charset=utf-8"));

            response.getOutputStream(true).write(Exceptions.getStackTrace(e).getBytes("UTF-8"));
        } catch (Throwable e1) {
            throw new WTFException(e1);
        }

    }

    @Override
    public boolean onPostRequest(PostRequest request, HttpResponse response) throws BasicRemoteAPIException {
        if (!StringUtils.equals(request.getRequestedPath(), "/" + Hash.getMD5(this.challenge.getPlugin().getClass().getName()))) {
            return false;
        }

        try {
            String pDo = request.getParameterbyKey("do");
            String id = request.getParameterbyKey("id");
            if (!StringUtils.equals(id, this.id.getID() + "")) {
                return false;
            }
            return challenge.onPostRequest(this, request, response);

        } catch (Throwable e) {
            error(response, e);
            return true;
        }

    }

    public abstract void onResponse(String request);

}
