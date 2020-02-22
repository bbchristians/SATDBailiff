package se.rit.edu.satd;

import com.sun.istack.internal.NotNull;
import se.rit.edu.util.GroupedComment;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class which stores information about the SATD Instance
 */
public class SATDInstance {

    // String constants for output
    public static final String COMMIT_UNKNOWN = "Commit Unknown";
    public static final String NO_COMMIT = "None";
    public static final String ERROR_FINDING_COMMIT = "ERROR_FINDING";
    public static final String FILE_UNKNOWN = "File Unknown";
    public static final String FILE_NONE = "None";
    public static final String FILE_DEV_NULL = "dev/null";
    public static final String ERROR_FINDING_FILE = "ERROR_FINDING";
    public static final String COMMENT_NONE = "None";

    // SATD Instance mandatory fields
    private String oldFile;
    private String newFile;
    private GroupedComment satdCommentBlock;

    @Deprecated
    private String satdComment;

    // SATD Instance other fields that maintain defaults
    private List<String> contributingCommits = new ArrayList<>();
    private String nameOfFileWhenAddressed = FILE_UNKNOWN;
    private String commitRemoved = COMMIT_UNKNOWN;
    private String commentChangedTo = COMMENT_NONE;
    private SATDResolution resolution = SATDResolution.UNKNOWN;

    @Deprecated
    public SATDInstance(@NotNull String oldFile, @NotNull String newFile, @NotNull String satdComment) {
        this.oldFile = oldFile;
        this.newFile = newFile;
        this.satdComment = satdComment;
    }

    public SATDInstance(@NotNull String oldFile, @NotNull String newFile, @NotNull GroupedComment satdComment) {
        this.oldFile = oldFile;
        this.newFile = newFile;
        this.satdCommentBlock = satdComment;
    }

    public List<String> getContributingCommits() {
        return this.contributingCommits;
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
        return this.satdCommentBlock.getComment();
    }

    public int getStartLineNumberOldFile() {
        return this.satdCommentBlock.getStartLine();
    }

    public int getEndLineNumberOldFile() {
        return this.satdCommentBlock.getEndLine();
    }

    public void addContributingCommit(String contributingCommit) {
        this.contributingCommits.add(contributingCommit);
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
        SATD_UNADDRESSED,
        ERROR_UNKNOWN
    }
}
