package org.jdownloader.jdserv;

import org.appwork.remoteapi.RemoteAPIInterface;
import org.appwork.remoteapi.RemoteAPIRequest;
import org.appwork.remoteapi.RemoteAPIResponse;
import org.appwork.remoteapi.annotations.ApiNamespace;
import org.appwork.remoteapi.exceptions.RemoteAPIException;

@ApiNamespace("RedirectInterface")
public interface RedirectInterface extends RemoteAPIInterface {
    //

    // public static CounterInterface INST =
    // JD_SERV_CONSTANTS.create(CounterInterface.class);
    void banner(RemoteAPIRequest request, RemoteAPIResponse response, String source, String md5, String lng, boolean hasUploaded, boolean hasOthers);

    void ul(RemoteAPIRequest request, RemoteAPIResponse response, String source, String sig, String uid, String pid, boolean hasUploaded, boolean hasOthers);

    void redirect(String url, RemoteAPIRequest request, RemoteAPIResponse response);

    void banner(RemoteAPIRequest request, RemoteAPIResponse response, String md5, String sig, String uid, String pid, String source, String lng, boolean hasUploaded, boolean hasOthers);

    void submitKayakoTicket(String email, String name, String subject, String text) throws RemoteAPIException;

}
