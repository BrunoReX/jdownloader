package org.jdownloader.api.extraction;

import java.util.HashMap;

import org.appwork.storage.Storable;
import org.jdownloader.extensions.extraction.Archive;

public class ArchiveStatusStorable implements Storable {

    private String           archiveId        = null;
    private long             controllerId     = -1;
    private ControllerStatus controllerStatus = ControllerStatus.NA;
    private String           type             = null;

    public ControllerStatus getControllerStatus() {
        return controllerStatus;
    }

    public void setControllerStatus(ControllerStatus controllerStatus) {
        this.controllerStatus = controllerStatus;
    }

    public long getControllerId() {
        return controllerId;
    }

    public void setControllerId(long controllerId) {
        this.controllerId = controllerId;
    }

    private String                             archiveName = null;
    private HashMap<String, ArchiveFileStatus> states      = null;

    public ArchiveStatusStorable(/* Storable */) {

    }

    public ArchiveStatusStorable(HashMap<String, ArchiveFileStatus> states) {
        this(null, null, states);
    }

    public ArchiveStatusStorable(String archiveId, String name, HashMap<String, ArchiveFileStatus> states) {
        this.setStates(states);
        this.setArchiveId(archiveId);
        this.archiveName = name;
    }

    public ArchiveStatusStorable(Archive archive, HashMap<String, ArchiveFileStatus> states) {
        this.setStates(states);
        this.setArchiveId(archive.getArchiveID());
        this.setArchiveName(archive.getName());
        if (archive.getArchiveType() != null) {
            this.setType(archive.getArchiveType().name());
        } else if (archive.getSplitType() != null) {
            this.setType(archive.getSplitType().name());
        }
    }

    public String getArchiveId() {
        return archiveId;
    }

    public void setArchiveId(String archiveId) {
        this.archiveId = archiveId;
    }

    public String getArchiveName() {
        return archiveName;
    }

    public void setArchiveName(String archiveName) {
        this.archiveName = archiveName;
    }

    public HashMap<String, ArchiveFileStatus> getStates() {
        return states;
    }

    public void setStates(HashMap<String, ArchiveFileStatus> states) {
        this.states = states;
    }

    public String getType() {
        return type;
    }

    public void setType(String archiveType) {
        this.type = archiveType;
    }

    public static enum ArchiveFileStatus {
        COMPLETE,
        INCOMPLETE,
        MISSING;
    }

    public static enum ControllerStatus {
        RUNNING,
        QUEUED,
        NA
    }

}
