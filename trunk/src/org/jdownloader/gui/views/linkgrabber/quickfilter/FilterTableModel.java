package org.jdownloader.gui.views.linkgrabber.quickfilter;

import java.awt.Color;
import java.util.EventObject;

import javax.swing.Icon;

import net.miginfocom.swing.MigLayout;

import org.appwork.swing.exttable.ExtTableModel;
import org.appwork.swing.exttable.columns.ExtCheckColumn;
import org.appwork.swing.exttable.columns.ExtLongColumn;
import org.appwork.swing.exttable.columns.ExtTextColumn;
import org.appwork.utils.swing.SwingUtils;

public class FilterTableModel extends ExtTableModel<Filter> {

    /**
     *
     */
    private static final long serialVersionUID = 1749243877638799385L;

    public FilterTableModel() {
        super("FilterTableModel");

    }

    @Override
    protected void initColumns() {
        addColumn(new ExtTextColumn<Filter>("Hoster") {
            /**
             *
             */
            private static final long serialVersionUID = 1L;

            // {
            // renderer.setLayout(new MigLayout("ins 0", "[grow,fill][]",
            // "[]"));
            //
            // }
            @Override
            public int getMaxWidth() {
                return 18;
            }

            @Override
            public int getMinWidth() {
                return getMaxWidth();
            }

            @Override
            public int getDefaultWidth() {
                return getMaxWidth();
            }

            @Override
            public boolean isEnabled(Filter obj) {
                return obj.isEnabled();
            }

            @Override
            protected Icon getIcon(Filter value) {
                return value.getIcon();
            }

            @Override
            public boolean isSortable(Filter obj) {
                return false;
            }

            @Override
            public String getStringValue(Filter value) {
                return "";
            }
        });
        addColumn(new ExtLongColumn<Filter>("Hoster") {
            private final Color defaultColor;
            private final int   defaultMaxWidth;

            {
                defaultColor = renderer.getForeground();
                defaultMaxWidth = (int) SwingUtils.getStringSizeForFont("1000000", renderer.getFont()).getWidth() + 10;// border
            }

            @Override
            public int getMaxWidth() {
                return defaultMaxWidth;
            }

            @Override
            public int getMinWidth() {
                return getMaxWidth();
            }

            @Override
            public int getDefaultWidth() {
                return getMaxWidth();
            }

            @Override
            public boolean isEnabled(Filter obj) {
                return true;
            }

            @Override
            protected String getTooltipText(final Filter obj) {
                return obj.getDescription();
            }

            @Override
            public boolean isSortable(Filter obj) {
                return false;
            }

            @Override
            public void configureRendererComponent(Filter value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.configureRendererComponent(value, isSelected, hasFocus, row, column);
                final long num = getLong(value);
                if (num < 0) {
                    renderer.setText("");
                    renderer.setForeground(defaultColor);
                } else if (num > 0 && !value.isEnabled()) {
                    renderer.setForeground(Color.RED);
                } else {
                    renderer.setForeground(defaultColor);
                }

            }

            @Override
            protected long getLong(Filter value) {
                return value.getCounter();
            }

        });
        ExtTextColumn<Filter> hosterColumn;
        addColumn(hosterColumn = new ExtTextColumn<Filter>("Hoster") {
            private final Color defaultColor;

            {
                renderer.setLayout(new MigLayout("ins 0", "[grow,fill][]", "[grow,fill]"));
                defaultColor = rendererField.getForeground();
            }

            @Override
            public boolean isEnabled(Filter obj) {
                return true;
            }

            @Override
            protected String getTooltipText(final Filter obj) {
                return obj.getDescription();
            }

            @Override
            public void configureRendererComponent(Filter value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.configureRendererComponent(value, isSelected, hasFocus, row, column);
                if (value.getCounter() > 0 && !value.isEnabled()) {
                    rendererField.setForeground(Color.RED);
                } else {
                    rendererField.setForeground(defaultColor);
                }
            }

            @Override
            public boolean isSortable(Filter obj) {
                return true;
            }

            @Override
            public String getStringValue(Filter value) {
                return value.getName();
            }
        });
        addColumn(new ExtCheckColumn<Filter>("Check") {
            @Override
            public boolean isSortable(Filter obj) {
                return false;
            }

            @Override
            protected String getTooltipText(final Filter obj) {

                return obj.getDescription();
            }

            @Override
            protected boolean getBooleanValue(Filter value) {
                return value.isEnabled();
            }

            @Override
            public int getMaxWidth() {
                return 30;
            }

            @Override
            public int getMinWidth() {
                return getMaxWidth();
            }

            @Override
            public int getDefaultWidth() {
                return getMaxWidth();
            }

            @Override
            public boolean shouldSelectCell(final EventObject anEvent) {
                return false;
            }

            @Override
            public boolean isEditable(Filter obj) {
                return true;
            }

            @Override
            protected void setBooleanValue(boolean value, Filter object) {
                object.setEnabled(value);
            }
        });
        this.setSortColumn(hosterColumn);
    }

}
