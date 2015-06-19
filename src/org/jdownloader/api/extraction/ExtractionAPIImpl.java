package org.jdownloader.api.extraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jd.controlling.downloadcontroller.DownloadController;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

import org.jdownloader.api.RemoteAPIController;
import org.jdownloader.api.extraction.ArchiveStatusStorable.ArchiveFileStatus;
import org.jdownloader.api.utils.PackageControllerUtils;
import org.jdownloader.extensions.extraction.Archive;
import org.jdownloader.extensions.extraction.ArchiveFile;
import org.jdownloader.extensions.extraction.DummyArchive;
import org.jdownloader.extensions.extraction.DummyArchiveFile;
import org.jdownloader.extensions.extraction.ExtractionController;
import org.jdownloader.extensions.extraction.ExtractionExtension;
import org.jdownloader.extensions.extraction.contextmenu.downloadlist.ArchiveValidator;
import org.jdownloader.extensions.extraction.multi.CheckException;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.myjdownloader.client.bindings.interfaces.ExtractionInterface;

public class ExtractionAPIImpl implements ExtractionAPI {

    private final PackageControllerUtils<FilePackage, DownloadLink> packageControllerUtils;

    public ExtractionAPIImpl() {
        RemoteAPIController.validateInterfaces(ExtractionAPI.class, ExtractionInterface.class);
        packageControllerUtils = new PackageControllerUtils<FilePackage, DownloadLink>(DownloadController.getInstance());
    }

    @Override
    public void addArchivePassword(String password) {
        ExtractionExtension.getInstance().addPassword(password);
    }

    @Override
    public HashMap<String, Boolean> startExtractionNow(final long[] linkIds, final long[] packageIds) {
        final HashMap<String, Boolean> ret = new HashMap<String, Boolean>();
        final ExtractionExtension extension = ExtractionExtension.getInstance();
        if (extension != null) {
            final SelectionInfo<FilePackage, DownloadLink> selection = packageControllerUtils.getSelectionInfo(linkIds, packageIds);
            if (selection != null && !selection.isEmpty()) {
                final List<Archive> archives = ArchiveValidator.getArchivesFromPackageChildren(selection.getChildren());
                if (archives != null && !archives.isEmpty()) {
                    for (Archive archive : archives) {
                        final String archiveId = archive.getFactory().getID();
                        try {
                            final DummyArchive da = extension.createDummyArchive(archive);
                            if (da.isComplete()) {
                                extension.addToQueue(archive, true);
                                ret.put(archiveId, true);
                            } else {
                                ret.put(archiveId, false);
                            }
                        } catch (CheckException e) {
                            ret.put(archiveId, false);
                        }
                    }
                }
            }
        }
        return ret;
    }

    public List<ArchiveStatusStorable> getArchiveInfo(final long[] linkIds, final long[] packageIds) {
        final List<ArchiveStatusStorable> ret = new ArrayList<ArchiveStatusStorable>();
        final ExtractionExtension extension = ArchiveValidator.EXTENSION;
        if (extension != null) {
            final SelectionInfo<FilePackage, DownloadLink> selection = packageControllerUtils.getSelectionInfo(linkIds, packageIds);
            if (selection != null && !selection.isEmpty()) {
                final List<Archive> archives = ArchiveValidator.getArchivesFromPackageChildren(selection.getChildren());
                if (archives != null && !archives.isEmpty()) {
                    for (Archive archive : archives) {
                        final String archiveId = archive.getFactory().getID();
                        final String archiveName = archive.getName();
                        final HashMap<String, ArchiveFileStatus> extractionStates = new HashMap<String, ArchiveFileStatus>();
                        for (ArchiveFile file : archive.getArchiveFiles()) {
                            DummyArchiveFile da = new DummyArchiveFile(file);
                            if (da.isIncomplete()) {
                                if (da.isMissing()) {
                                    extractionStates.put(file.getName(), ArchiveFileStatus.MISSING);
                                } else {
                                    extractionStates.put(file.getName(), ArchiveFileStatus.INCOMPLETE);
                                }
                            } else {
                                extractionStates.put(file.getName(), ArchiveFileStatus.COMPLETE);
                            }
                        }
                        ArchiveStatusStorable archiveStatus = new ArchiveStatusStorable(archiveId, archiveName, extractionStates);
                        ret.add(archiveStatus);
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public Boolean cancelExtraction(long archiveId) {
        final ExtractionExtension extension = ArchiveValidator.EXTENSION;
        if (extension != null) {
            final List<ExtractionController> jobs = extension.getJobQueue().getJobs();
            if (jobs != null && !jobs.isEmpty()) {
                final String archiveID = Long.toString(archiveId);
                for (final ExtractionController controller : jobs) {
                    if (archiveID.equals(controller.getArchive().getFactory().getID())) {
                        return extension.cancel(controller);
                    }
                }
            }
        }
        return false;
    }
}
