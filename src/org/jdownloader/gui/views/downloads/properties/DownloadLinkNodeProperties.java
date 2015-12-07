package org.jdownloader.gui.views.downloads.properties;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.controlling.packagecontroller.AbstractNode;
import jd.controlling.packagecontroller.AbstractPackageChildrenNode;
import jd.controlling.packagecontroller.AbstractPackageNode;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

import org.appwork.utils.StringUtils;
import org.jdownloader.controlling.Priority;
import org.jdownloader.extensions.extraction.Archive;
import org.jdownloader.extensions.extraction.BooleanStatus;
import org.jdownloader.extensions.extraction.contextmenu.downloadlist.ArchiveValidator;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.gui.views.components.packagetable.LinkTreeUtils;
import org.jdownloader.gui.views.downloads.action.SetDownloadFolderInDownloadTableAction;

public class DownloadLinkNodeProperties extends AbstractNodeProperties {

    private final DownloadLink currentLink;
    private final FilePackage  currentPackage;
    private List<Archive>      archives = null;

    public DownloadLinkNodeProperties(DownloadLink downloadLink) {
        this.currentLink = downloadLink;
        this.currentPackage = downloadLink.getFilePackage();
    }

    @Override
    protected List<Archive> loadArchives() {
        if (archives == null) {
            final ArrayList<DownloadLink> children = new ArrayList<DownloadLink>();
            children.add(currentLink);
            archives = ArchiveValidator.getArchivesFromPackageChildren(children);
        }
        return archives;
    }

    @Override
    protected String loadComment() {
        return currentLink.getComment();
    }

    protected DownloadLink getDownloadLink() {
        return currentLink;
    }

    protected FilePackage getFilePackage() {
        return currentPackage;
    }

    @Override
    protected String loadDownloadFrom() {
        final String dlLink = currentLink.getView().getDisplayUrl();
        if (dlLink == null) {
            return "*******************************";
        }
        return dlLink;
    }

    @Override
    protected String loadDownloadPassword() {
        return currentLink.getDownloadPassword();
    }

    @Override
    protected String loadFilename() {
        return currentLink.getView().getDisplayName();
    }

    @Override
    protected String loadMD5() {
        return currentLink.getMD5Hash();
    }

    @Override
    protected String loadPackageName() {
        return currentPackage.getName();
    }

    @Override
    protected Priority loadPriority() {
        return currentLink.getPriorityEnum();
    }

    @Override
    protected String loadSaveTo() {
        return LinkTreeUtils.getRawDownloadDirectory(currentPackage).getAbsolutePath();
    }

    @Override
    protected String loadSha1() {
        return currentLink.getSha1Hash();
    }

    @Override
    protected void saveComment(String text) {
        currentLink.getDownloadLink().setComment(text);
    }

    @Override
    protected void saveDownloadPassword(String text) {
        currentLink.getDownloadLink().setDownloadPassword(text);
    }

    @Override
    protected void saveFilename(final String name) {
        final String curForced = currentLink.getForcedFileName();
        if (StringUtils.equals(name, curForced)) {
            return;
        }
        final String curName = currentLink.getName(false, true);
        if (StringUtils.equals(name, curName) && curForced == null) {
            // name equals normal name
            return;
        }
        DownloadWatchDog.getInstance().renameLink(currentLink, curName);
    }

    @Override
    protected void saveMd5(String cs) {
        currentLink.getDownloadLink().setMD5Hash(cs);
    }

    @Override
    protected void savePackageName(String text) {
        currentPackage.setName(text);
    }

    @Override
    protected void savePriority(Priority priop) {
        currentLink.setPriorityEnum(priop);
    }

    @Override
    protected void saveSaveTo(final String stringpath) {
        new SetDownloadFolderInDownloadTableAction(new SelectionInfo<FilePackage, DownloadLink>(currentLink)) {
            /**
             *
             */
            private static final long serialVersionUID = -3767832209839199384L;

            protected java.io.File dialog(java.io.File path) throws org.appwork.utils.swing.dialog.DialogClosedException, org.appwork.utils.swing.dialog.DialogCanceledException {
                return new File(stringpath);
            };
        }.actionPerformed(null);

        // currentPackage.setDownloadDirectory(PackagizerController.replaceDynamicTags(destination.getPath(), currentPackage.getName()));
    }

    @Override
    protected void saveSha1(String cs) {
        currentLink.getDownloadLink().setSha1Hash(cs);
    }

    @Override
    protected void saveArchivePasswords(List<String> hashSet) {
        if (archives != null && archives.size() == 1) {
            archives.get(0).getSettings().setPasswords(hashSet);
        }
    }

    @Override
    protected void saveAutoExtract(BooleanStatus selectedItem) {
        if (archives != null && archives.size() == 1) {
            archives.get(0).getSettings().setAutoExtract(selectedItem);
        }
    }

    @Override
    protected boolean samePackage(AbstractPackageNode pkg) {
        return currentPackage == pkg;
    }

    @Override
    protected boolean isDifferent(AbstractNode node) {
        if (node != null && node instanceof AbstractPackageChildrenNode) {
            final AbstractPackageChildrenNode child = (AbstractPackageChildrenNode) node;
            return currentLink != child || child.getParentNode() != currentPackage;
        }
        return true;
    }

    @Override
    protected boolean hasLoadedArchives() {
        return archives != null;
    }

}
