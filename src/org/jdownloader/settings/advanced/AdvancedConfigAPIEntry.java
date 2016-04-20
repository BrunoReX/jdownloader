package org.jdownloader.settings.advanced;

import java.io.File;

import org.appwork.exceptions.WTFException;
import org.appwork.remoteapi.annotations.AllowNonStorableObjects;
import org.appwork.storage.Storable;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.utils.Application;
import org.appwork.utils.Files;
import org.appwork.utils.StringUtils;
import org.jdownloader.myjdownloader.client.bindings.AdvancedConfigEntryDataStorable;

public class AdvancedConfigAPIEntry extends AdvancedConfigEntryDataStorable implements Storable {

    public AdvancedConfigAPIEntry(AdvancedConfigEntry entry, boolean returnDescription, boolean addValue, boolean defaultValues) {
        KeyHandler<?> kh = entry.getKeyHandler();
        if (returnDescription && StringUtils.isNotEmpty(entry.getDescription())) {
            setDocs(entry.getDescription());
        }

        setType(kh.getTypeString());
        String i = kh.getStorageHandler().getConfigInterface().getName();
        setInterfaceName(i);

        setKey(createKey(kh));

        try {
            AbstractType abstractType = AbstractType.valueOf(kh.getAbstractType().name());
            setAbstractType(abstractType);
        } catch (Exception e) {
            throw new WTFException(e);

        }

        File expectedPath = Application.getResource("cfg/" + i);
        String storage = null;
        if (!expectedPath.equals(kh.getStorageHandler().getPath())) {
            storage = Files.getRelativePath(Application.getTemp().getParentFile(), kh.getStorageHandler().getPath());
            if (StringUtils.isEmpty(storage)) {
                storage = kh.getStorageHandler().getPath().getAbsolutePath();
            }
        }
        if (storage != null) {
            setStorage(storage);

        }
        Object value = kh.getValue();
        if (addValue) {
            setValue(value);

        }
        if (defaultValues) {
            Object def = entry.getDefault();

            setDefaultValue(def);

        }

    }

    protected String createKey(KeyHandler<?> kh) {
        final String getterName = kh.getGetMethod().getName();
        if (getterName.startsWith("is")) {
            return getterName.substring(2);
        } else if (getterName.startsWith("get")) {
            return getterName.substring(3);
        }
        return getterName;
    }

    @AllowNonStorableObjects
    @Override
    public Object getDefaultValue() {
        return super.getDefaultValue();
    }

    @Override
    @AllowNonStorableObjects
    public Object getValue() {
        return super.getValue();
    }

    @SuppressWarnings("unused")
    public AdvancedConfigAPIEntry(/* Storable */) {
        super();
    }

}
