package jd.controlling.reconnect.pluginsinc.upnp;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.logging.Logger;

import jd.controlling.reconnect.ReconnectException;
import jd.controlling.reconnect.ReconnectInvoker;
import jd.controlling.reconnect.ReconnectResult;
import jd.controlling.reconnect.pluginsinc.upnp.translate.T;

import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.net.httpconnection.HTTPConnection;
import org.appwork.utils.net.httpconnection.HTTPConnection.RequestMethod;
import org.appwork.utils.net.httpconnection.HTTPConnectionImpl;

public class UPNPReconnectInvoker extends ReconnectInvoker {

    private final String serviceType;

    public UPNPReconnectInvoker(UPNPRouterPlugin upnpRouterPlugin, String serviceType2, String controlURL2) {
        super(upnpRouterPlugin);
        this.serviceType = serviceType2;
        this.controlURL = controlURL2;
    }

    private static String internalSendRequest(final Logger logger, final String serviceType, final String controlUrl, final String command) throws IOException {
        final String data = "<?xml version='1.0' encoding='utf-8'?> <s:Envelope s:encodingStyle='http://schemas.xmlsoap.org/soap/encoding/' xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'> <s:Body> <u:" + command + " xmlns:u='" + serviceType + "' /> </s:Body> </s:Envelope>";
        // this works for fritz box.
        // old code did NOT work:

        /*
         * 
         * final String data = "<?xml version=\"1.0\"?>\n" +
         * "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n"
         * + " <s:Body>\n  <m:" + command + " xmlns:m=\"" + serviceType + "\"></m:" + command + ">\n </s:Body>\n</s:Envelope>"; try { final
         * URL url = new URL(controlUrl); final URLConnection conn = url.openConnection(); conn.setDoOutput(true);
         * conn.addRequestProperty("Content-Type", "text/xml; charset=\"utf-8\""); conn.addRequestProperty("SOAPAction", serviceType + "#" +
         * command + "\""); p
         */
        final URL url = new URL(controlUrl);
        final HTTPConnection con = new HTTPConnectionImpl(url);
        if ("GetExternalIPAddress".equalsIgnoreCase(command)) {
            con.setReadTimeout(2000);
        }
        con.setAllowedResponseCodes(new int[] { -1 });
        con.setRequestMethod(RequestMethod.POST);
        con.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        con.setRequestProperty("SOAPAction", serviceType + "#" + command);
        byte datas[] = data.getBytes("UTF-8");
        con.setRequestProperty("Content-Length", datas.length + "");
        con.setRequestProperty("Connection", "close");
        BufferedReader rd = null;
        if (logger != null) {
            logger.info(con + "");
        }
        try {
            con.connect();
            con.getOutputStream().write(datas);
            con.getOutputStream().flush();
            rd = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String xmlstr = "";
            String nextln;
            try {
                // missing eof fpor some routers? fritz box?
                while ((nextln = rd.readLine()) != null) {
                    xmlstr += nextln.trim();
                    System.out.println(nextln);
                    if (nextln.toLowerCase(Locale.ENGLISH).contains("</s:body>")) {
                        break;
                    }
                }
            } catch (IOException e) {
                logger.info(con.toString());
                LogSource.exception(logger, e);
                if (!StringUtils.isEmpty(xmlstr)) {
                    return xmlstr;

                } else {
                    throw e;
                }
            }
            if (logger != null) {
                logger.info(con + "");
                logger.info(xmlstr);
            }
            return xmlstr;
        } finally {
            try {
                rd.close();
            } catch (final Throwable e) {
            }
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
            if (logger != null) {
                logger.info(con + "");
            }
        }

    }

    public String getServiceType() {
        return serviceType;
    }

    public String getControlURL() {
        return controlURL;
    }

    private final String controlURL;

    public String getName() {
        return T.T.UPNPReconnectInvoker_getName_();
    }

    public static String sendRequest(final Logger logger, final String serviceType, final String controlUrl, final String command) throws IOException {
        try {
            final String result = internalSendRequest(logger, serviceType, controlUrl, command);
            if (!StringUtils.containsIgnoreCase(result, "UPnPError")) {
                return result;
            } else {
                Thread.sleep(1000);
                return internalSendRequest(logger, serviceType, controlUrl, command);
            }
        } catch (final EOFException e) {
            if (StringUtils.contains("empty HTTP-Response", e.getMessage())) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw e;
                }
                return internalSendRequest(logger, serviceType, controlUrl, command);
            }
            throw e;
        } catch (final InterruptedException e) {
            throw new IOException(e);
        }
    }

    private static String sendForceTermination(final Logger logger, final String serviceType, final String controlUrl) throws IOException {
        return sendRequest(logger, serviceType, controlUrl, "ForceTermination");
    }

    private static String sendRequestConnection(final Logger logger, final String serviceType, final String controlUrl) throws IOException {
        return sendRequest(logger, serviceType, controlUrl, "RequestConnection");
    }

    @Override
    public void run() throws ReconnectException, InterruptedException {
        try {
            logger.info("RUN");
            if (StringUtils.isEmpty(getServiceType())) {
                throw new ReconnectException(T.T.malformedservicetype());
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            sendForceTermination(getLogger(), getServiceType(), getControlURL());
            Throwable throwable = null;
            for (int requestTry = 0; requestTry < 10; requestTry++) {
                try {
                    Thread.sleep(2000);
                    try {
                        final String result = sendRequestConnection(getLogger(), getServiceType(), getControlURL());
                        if (!StringUtils.containsIgnoreCase(result, "UPnPError")) {
                            return;
                        }
                    } catch (final Throwable e) {
                        throwable = e;
                        logger.log(e);
                    }
                } catch (InterruptedException ie) {
                    try {
                        final String result = sendRequestConnection(getLogger(), getServiceType(), getControlURL());
                        if (!StringUtils.containsIgnoreCase(result, "UPnPError")) {
                            return;
                        }
                    } catch (final Throwable e) {
                        logger.log(e);
                    }
                    throw ie;
                }
            }
            if (throwable != null) {
                throw throwable;
            }
        } catch (MalformedURLException e) {
            throw new ReconnectException(T.T.malformedurl());
        } catch (InterruptedException e) {
            throw e;
        } catch (final Throwable e) {
            throw new ReconnectException(e);
        }
    }

    @Override
    protected ReconnectResult createReconnectResult() {
        return new UPNPReconnectResult();
    }

    @Override
    protected void testRun() throws ReconnectException, InterruptedException {
        run();
    }

}
