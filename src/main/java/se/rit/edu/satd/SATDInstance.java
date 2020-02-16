package se.rit.edu.satd;

import com.sun.istack.internal.NotNull;

/**
 * Data class which stores information about the SATD Instance
 */
public class SATDInstance {

    // String constants for output
    public static final String COMMIT_UNKNOWN = "Commit Unknown";
    public static final String NO_COMMIT = "None";
    public static final String FILE_UNKNOWN = "File Unknown";
    public static final String FILE_NONE = "None";
    public static final String COMMENT_NONE = "None";

    // SATD Instance mandatory fields
    private String oldFile;
    private String newFile;
    private String satdComment;

    // SATD Instance other fields that maintain defaults
    private String commitAdded = COMMIT_UNKNOWN;
    private String nameOfFileWhenAddressed = FILE_UNKNOWN;
    private String commitRemoved = COMMIT_UNKNOWN;
    private String commentChangedTo = COMMENT_NONE;
    private SATDResolution resolution = SATDResolution.UNKNOWN;

    public SATDInstance(@NotNull String oldFile, @NotNull String newFile, @NotNull String satdComment) {
        this.oldFile = oldFile;
        this.newFile = newFile;
        this.satdComment = satdComment;
    }

    public String getCommitAdded() {
        return commitAdded;
    }

    public String getCommitRemoved() {
        return commitRemoved;
    }

    public String getNameOfFileWhenAddressed() {
        return nameOfFileWhenAddressed;
    }

    public SATDResolution getResolution() {
        return resolution;
    }

    public String getCommentChangedTo() {
        return this.commentChangedTo;
    }

    public String getOldFile() {
        return this.oldFile;
    }

    public String getNewFile() {
        return this.newFile;
    }

    public String getSATDComment() {
        return this.satdComment;
    }

    public void setCommitAdded(String commitAdded) {
        this.commitAdded = commitAdded;
    }

    public void setCommitRemoved(String commitRemoved) {
        this.commitRemoved = commitRemoved;
    }

    public void setNameOfFileWhenAddressed(String nameOfFileWhenAddressed) {
        this.nameOfFileWhenAddressed = nameOfFileWhenAddressed;
    }

    public void setResolution(SATDResolution resolution) {
        this.resolution = resolution;
    }

    public void setCommentChangedTo(String comment) {
        this.commentChangedTo = comment;
    }

    public void setNewFile(String newFile) {
        this.newFile = newFile;
    }

    public enum SATDResolution {
        UNKNOWN,
        FILE_REMOVED,
        FILE_PATH_CHANGED,
        SATD_REMOVED,
        SATD_POSSIBLY_REMOVED,
        SATD_CHANGED,
        SATD_ADDED,
        SATD_UNADDRESSED
    }
}
