package jd.controlling.authentication;

import java.util.ArrayList;

import org.appwork.storage.config.ConfigInterface;
import org.appwork.storage.config.annotations.DefaultJsonObject;

public interface AuthenticationControllerSettings extends ConfigInterface {
    @DefaultJsonObject("[]")
    ArrayList<AuthenticationInfo> getList();

    void setList(ArrayList<AuthenticationInfo> list);
}
