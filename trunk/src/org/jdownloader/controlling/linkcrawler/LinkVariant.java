package org.jdownloader.controlling.linkcrawler;

import javax.swing.Icon;

public interface LinkVariant {
    // _ because often LinkVariants will be storables. and we do not want to store these getters
    String _getUniqueId();

    String _getName(Object caller);

    Icon _getIcon(Object caller);

    String _getTooltipDescription(Object caller);

}
