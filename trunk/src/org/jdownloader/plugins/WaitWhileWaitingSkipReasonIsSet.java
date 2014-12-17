package org.jdownloader.plugins;

import javax.swing.Icon;

import jd.controlling.downloadcontroller.HistoryEntry;
import jd.controlling.packagecontroller.AbstractNode;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackageView;

import org.jdownloader.api.downloads.ChannelCollector;
import org.jdownloader.api.downloads.DownloadControllerEventPublisher;
import org.jdownloader.api.downloads.v2.DownloadsAPIV2Impl;
import org.jdownloader.gui.views.downloads.columns.TaskColumn;

public class WaitWhileWaitingSkipReasonIsSet implements ConditionalSkipReason, DownloadLinkCondition, ValidatableConditionalSkipReason {

    private final WaitingSkipReason reason;
    private final DownloadLink      source;
    private boolean                 valid = true;

    public WaitWhileWaitingSkipReasonIsSet(WaitingSkipReason reason, DownloadLink source) {
        this.reason = reason;
        this.source = source;
    }

    @Override
    public boolean isConditionReached() {
        return source.getConditionalSkipReason() != this || reason.isConditionReached();
    }

    @Override
    public String getMessage(Object requestor, AbstractNode node) {
        if (source == node) {
            return reason.getMessage(requestor, node);
        } else if (requestor instanceof TaskColumn || requestor instanceof FilePackageView || requestor instanceof HistoryEntry) {
            return reason.getMessage(requestor, node);
        } else if (requestor instanceof DownloadControllerEventPublisher) {
            return reason.getMessage(requestor, node);
        } else if (requestor instanceof DownloadsAPIV2Impl) {
            return reason.getMessage(requestor, node);
        } else if (requestor instanceof ChannelCollector) {
            return reason.getMessage(requestor, node);
        }
        return null;
    }

    @Override
    public Icon getIcon(Object requestor, AbstractNode node) {
        if (source == node) {
            return reason.getIcon(requestor, node);
        } else if (requestor instanceof TaskColumn || requestor instanceof FilePackageView || requestor instanceof HistoryEntry) {
            return reason.getIcon(requestor, node);
        }
        return null;
    }

    @Override
    public void finalize(DownloadLink link) {
    }

    public WaitingSkipReason.CAUSE getCause() {
        return reason.getCause();
    }

    @Override
    public DownloadLink getDownloadLink() {
        return source;
    }

    public ConditionalSkipReason getConditionalSkipReason() {
        return reason;
    }

    @Override
    public boolean isValid() {
        if (valid == false) {
            return false;
        }
        return reason.isValid();
    }

    @Override
    public void invalidate() {
        valid = false;
    }

}
