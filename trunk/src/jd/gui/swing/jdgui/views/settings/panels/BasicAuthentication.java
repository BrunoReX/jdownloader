//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.gui.swing.jdgui.views.settings.panels;

import javax.swing.Icon;

import org.appwork.utils.event.queue.Queue.QueuePriority;
import org.appwork.utils.event.queue.QueueAction;
import org.jdownloader.auth.AuthenticationController;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.settings.AbstractConfigPanel;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.translate._JDT;

import jd.controlling.TaskQueue;
import jd.gui.swing.jdgui.views.settings.panels.basicauthentication.BasicAuthenticationPanel;

public class BasicAuthentication extends AbstractConfigPanel {

    private static final long serialVersionUID = -7963763730328793139L;

    public String getTitle() {
        return _JDT.T.gui_settings_basicauth_title();
    }

    public BasicAuthentication() {
        super();
        this.addHeader(getTitle(), new AbstractIcon(IconKey.ICON_BASICAUTH, 32));
        this.addDescriptionPlain(_JDT.T.gui_settings_basicauth_description());
        add(BasicAuthenticationPanel.getInstance());
    }

    @Override
    public Icon getIcon() {
        return new AbstractIcon(IconKey.ICON_BASICAUTH, 32);
    }

    @Override
    public void save() {
    }

    @Override
    public void updateContents() {
        TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>(QueuePriority.HIGH) {

            @Override
            protected Void run() throws RuntimeException {
                if (isShown()) {
                    BasicAuthenticationPanel.getInstance().getTable().getModel()._fireTableStructureChanged(AuthenticationController.getInstance().list(), false);
                }
                return null;
            }
        });
    }
}