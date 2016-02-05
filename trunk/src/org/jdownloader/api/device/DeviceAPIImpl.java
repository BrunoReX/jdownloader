package org.jdownloader.api.device;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import jd.controlling.reconnect.ipcheck.BalancedWebIPCheck;
import jd.controlling.reconnect.ipcheck.IP;

import org.appwork.remoteapi.RemoteAPIRequest;
import org.appwork.utils.net.Base64OutputStream;
import org.appwork.utils.net.httpconnection.HTTPProxyUtils;
import org.jdownloader.api.myjdownloader.MyJDownloaderController;
import org.jdownloader.api.myjdownloader.MyJDownloaderDirectServer;
import org.jdownloader.api.myjdownloader.MyJDownloaderHttpConnection;
import org.jdownloader.myjdownloader.client.json.DirectConnectionInfo;
import org.jdownloader.myjdownloader.client.json.DirectConnectionInfos;

public class DeviceAPIImpl implements DeviceAPI {

    @Override
    public DirectConnectionInfos getDirectConnectionInfos(final RemoteAPIRequest request) {
        final MyJDownloaderDirectServer directServer = MyJDownloaderController.getInstance().getConnectThread().getDirectServer();
        final DirectConnectionInfos ret = new DirectConnectionInfos();
        if (directServer == null || !directServer.isAlive() || directServer.getLocalPort() < 0) {
            return ret;
        }
        final List<DirectConnectionInfo> infos = new ArrayList<DirectConnectionInfo>();
        final List<InetAddress> localIPs = HTTPProxyUtils.getLocalIPs(true);
        if (localIPs != null) {
            for (InetAddress localIP : localIPs) {
                DirectConnectionInfo info = new DirectConnectionInfo();
                info.setPort(directServer.getLocalPort());
                info.setIp(localIP.getHostAddress());
                infos.add(info);
            }
        }
        if (directServer.getRemotePort() > 0) {
            try {
                final IP externalIP = new BalancedWebIPCheck().getExternalIP();
                if (externalIP.getIP() != null) {
                    final DirectConnectionInfo info = new DirectConnectionInfo();
                    info.setPort(directServer.getRemotePort());
                    info.setIp(externalIP.getIP());
                    infos.add(info);
                }
            } catch (final Throwable e) {
                /* eg OfflineException */
            }
        }
        if (infos.size() > 0) {
            ret.setInfos(infos);
        }
        return ret;
    }

    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public String getSessionPublicKey(RemoteAPIRequest request) {
        final MyJDownloaderHttpConnection connection = MyJDownloaderHttpConnection.getMyJDownloaderHttpConnection(request);
        if (connection != null) {
            final KeyPair keyPair = connection.getRSAKeyPair();
            if (keyPair != null) {
                try {
                    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    final Base64OutputStream b64 = new Base64OutputStream(bos);
                    b64.write(keyPair.getPublic().getEncoded());
                    b64.close();
                    return bos.toString("UTF-8");
                } catch (IOException e) {
                    connection.getLogger().log(e);
                }
            }
        }
        return null;
    }

}
