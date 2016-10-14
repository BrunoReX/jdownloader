package org.jdownloader.images;

import java.awt.Image;

import javax.swing.Icon;

import org.appwork.resources.AWIcon;
import org.appwork.resources.IconRef;

public enum JDIconRef implements IconRef {
    updatericon("updatericon"),
    updaterIcon100("updaterIcon100"),
    updaterIcon0("updaterIcon0"),
    update("update"),
    popDownLarge(AWIcon.TABLE_COLUMN_COMBO_popDownLarge.path());
    private String path;

    /**
     *
     */
    private JDIconRef(String path) {
        this.path = path;
    }

    /**
     * @param i
     * @return
     */
    public Icon get(int size) {
        return NewTheme.I().getIcon(path, size);
    }

    public Icon icon(int size) {
        return NewTheme.I().getIcon(path, size);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.appwork.resources.IconRef#image(int)
     */
    @Override
    public Image image(int size) {
        return NewTheme.I().getImage(path, size);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.appwork.resources.IconRef#path()
     */
    @Override
    public String path() {
        // TODO Auto-generated method stub
        return path;
    }
}
