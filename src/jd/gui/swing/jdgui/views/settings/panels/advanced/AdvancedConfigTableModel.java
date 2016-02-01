package jd.gui.swing.jdgui.views.settings.panels.advanced;

import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.Locale;

import jd.controlling.ClipboardMonitoring;

import org.appwork.swing.exttable.ExtTableModel;
import org.appwork.swing.exttable.columns.ExtTextColumn;
import org.appwork.utils.StringUtils;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.settings.advanced.AdvancedConfigEntry;
import org.jdownloader.settings.advanced.AdvancedConfigManager;

public class AdvancedConfigTableModel extends ExtTableModel<AdvancedConfigEntry> {
    private static final long serialVersionUID = 1L;
    private volatile String   text             = null;

    public AdvancedConfigTableModel(String id) {
        super(id);

    }

    private boolean containsKeyword(final AdvancedConfigEntry configEntry, final String find) {
        if (configEntry != null && StringUtils.isNotEmpty(find)) {
            final String[] keywords = configEntry.getKeywords();
            if (keywords != null && keywords.length > 0) {
                for (String keyword : keywords) {
                    if (StringUtils.containsIgnoreCase(find, keyword)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void _fireTableStructureChanged(java.util.List<AdvancedConfigEntry> newtableData, boolean refreshSort) {
        final String ltext = text;
        if (ltext != null) {
            final String finds[] = ltext.replaceAll("[^a-zA-Z0-9 ]+", "").replace("colour", "color").replace("directory", "folder").toLowerCase(Locale.ENGLISH).split("\\s");
            if (finds.length > 0) {
                for (final Iterator<AdvancedConfigEntry> it = newtableData.iterator(); it.hasNext();) {
                    final AdvancedConfigEntry next = it.next();
                    if (StringUtils.startsWithCaseInsensitive(next.getInternalKey(), finds[0])) {
                        continue;
                    }
                    for (String find : finds) {
                        if (StringUtils.containsIgnoreCase(next.getKey(), find) || StringUtils.containsIgnoreCase(next.getDescription(), find) || containsKeyword(next, find) || StringUtils.containsIgnoreCase(next.getKeyText(), find)) {
                            continue;
                        } else {
                            it.remove();
                            break;
                        }
                    }
                }
            }
        }
        super._fireTableStructureChanged(newtableData, refreshSort);
    }

    @Override
    protected void initColumns() {
        addColumn(new ExtTextColumn<AdvancedConfigEntry>(_GUI._.AdvancedTableModel_initColumns_key_()) {
            private static final long serialVersionUID = 1L;

            @Override
            protected String getTooltipText(AdvancedConfigEntry obj) {
                return obj.getDescription();
            }

            @Override
            public String getStringValue(AdvancedConfigEntry value) {
                return value.getKeyText();
            }

            @Override
            public boolean isEditable(AdvancedConfigEntry obj) {
                return false;
            }

            @Override
            public boolean onDoubleClick(MouseEvent e, AdvancedConfigEntry obj) {
                ClipboardMonitoring.getINSTANCE().setCurrentContent(obj.getKey());
                return true;
            }

            @Override
            public int getDefaultWidth() {
                return 200;
            }

            @Override
            public boolean isHidable() {
                return false;
            }
        });
        addColumn(new ExtTextColumn<AdvancedConfigEntry>(_GUI._.AdvancedTableModel_initColumns_desc_()) {
            private static final long serialVersionUID = 1L;

            @Override
            protected String getTooltipText(AdvancedConfigEntry obj) {
                return obj.getDescription();
            }

            @Override
            public String getStringValue(AdvancedConfigEntry value) {
                return value.getDescription();
            }

            @Override
            public boolean onDoubleClick(MouseEvent e, AdvancedConfigEntry obj) {
                ClipboardMonitoring.getINSTANCE().setCurrentContent(obj.getDescription());
                return true;
            }

            @Override
            public boolean isEditable(AdvancedConfigEntry obj) {
                return false;
            }

            @Override
            public int getDefaultWidth() {
                return 200;
            }

            @Override
            public boolean isHidable() {
                return true;
            }
        });

        addColumn(new AdvancedValueColumn());
        addColumn(new ExtTextColumn<AdvancedConfigEntry>(_GUI._.AdvancedTableModel_initColumns_type_()) {
            private static final long serialVersionUID = 1L;

            @Override
            public int getDefaultWidth() {

                return 100;
            }

            public boolean isDefaultVisible() {
                return false;
            }

            @Override
            public String getStringValue(AdvancedConfigEntry value) {
                return value.getTypeString();
            }

        });
        addColumn(new EditColumn());
    }

    public void refresh(final String filterText) {
        this.text = filterText;
        _fireTableStructureChanged(AdvancedConfigManager.getInstance().list(), true);
    }

}
