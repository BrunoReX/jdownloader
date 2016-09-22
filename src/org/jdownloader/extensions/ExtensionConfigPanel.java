package org.jdownloader.extensions;

import java.awt.Component;
import java.util.ArrayList;

import javax.swing.Icon;

import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DevConfig;
import org.appwork.storage.config.events.ConfigEventListener;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.BooleanKeyHandler;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.utils.Application;
import org.appwork.utils.swing.dialog.Dialog;
import org.jdownloader.gui.settings.AbstractConfigPanel;
import org.jdownloader.images.NewTheme;
import org.jdownloader.settings.advanced.AdvancedConfigEntry;

public abstract class ExtensionConfigPanel<T extends AbstractExtension> extends AbstractConfigPanel implements ConfigEventListener {

    private static final long serialVersionUID = 1L;

    protected T               extension;

    private BooleanKeyHandler keyHandlerEnabled;

    public void onConfigValidatorError(KeyHandler<Object> keyHandler, Object invalidValue, ValidationException validateException) {
    }

    @Override
    public String getPanelID() {
        return getExtension().getClass().getSimpleName();
    }

    public ArrayList<AdvancedConfigEntry> register() {
        final ArrayList<AdvancedConfigEntry> configInterfaces = new ArrayList<AdvancedConfigEntry>();
        for (final KeyHandler m : getExtension().getSettings()._getStorageHandler().getKeyHandler()) {
            if (m.getAnnotation(AboutConfig.class) != null && (m.getAnnotation(DevConfig.class) == null || !Application.isJared(null))) {
                if (m.getSetMethod() == null) {
                    throw new RuntimeException("Setter for " + m.getKey() + " missing");
                } else if (m.getGetMethod() == null) {
                    throw new RuntimeException("Getter for " + m.getKey() + " missing");
                } else {
                    synchronized (configInterfaces) {
                        configInterfaces.add(new AdvancedConfigEntry(getExtension().getSettings(), m));
                    }
                }
            }
        }
        return configInterfaces;
    }

    protected String getHeaderName(T plg) {
        return plg.getName();
    }

    public ExtensionConfigPanel(T plg, boolean clean) {
        super();
        this.extension = plg;
        keyHandlerEnabled = plg.getSettings()._getStorageHandler().getKeyHandler("enabled", BooleanKeyHandler.class);

        plg.getSettings()._getStorageHandler().getEventSender().addListener(this);
        if (!clean) {
            final Header header = new Header(getHeaderName(plg), NewTheme.I().getIcon(extension.getIconKey(), 32), keyHandlerEnabled, extension.getVersion());

            add(header, "spanx,growx,pushx");

            header.setEnabled(plg.isEnabled());
            if (plg.getDescription() != null) {
                addDescription(plg.getDescription());
            }
            keyHandlerEnabled.getEventSender().addListener(new GenericConfigEventListener<Boolean>() {

                public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
                    try {
                        extension.setEnabled(header.isHeaderEnabled());
                        updateHeaders(header.isHeaderEnabled());
                    } catch (Exception e1) {
                        org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e1);
                        Dialog.getInstance().showExceptionDialog("Error", e1.getMessage(), e1);
                    }
                }

                public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
                }
            });
        }

    }

    public void onConfigValueModified(KeyHandler<Object> keyHandler, Object newValue) {

    }

    public ExtensionConfigPanel(T plg) {
        this(plg, false);

    }

    @Override
    protected void onShow() {
        super.onShow();
        updateHeaders(extension.isEnabled());
    }

    private void updateHeaders(boolean b) {
        for (Component c : this.getComponents()) {
            if (c instanceof Header) {
                ((Header) c).setHeaderEnabled(b);
            }
        }
    }

    @Override
    public Icon getIcon() {
        return NewTheme.I().getIcon(extension.getIconKey(), 32);
    }

    @Override
    public String getTitle() {
        return extension.getName();
    }

    public T getExtension() {
        return extension;
    }
}
