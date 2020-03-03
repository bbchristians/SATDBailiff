package se.rit.edu.satd;

import com.sun.istack.internal.NotNull;
import se.rit.edu.git.models.CommitMetaData;
import se.rit.edu.git.models.NullCommitMetaData;
import se.rit.edu.satd.comment.GroupedComment;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class which stores information about the SATD Instance
 */
public class SATDInstance {

    // String constants for output
    public static final String COMMIT_UNKNOWN = "Commit Unknown";
    public static final String FILE_UNKNOWN = "File Unknown";
    public static final String FILE_NONE = "None";
    public static final String FILE_DEV_NULL = "dev/null";
    public static final String COMMENT_NONE = "None";

    // SATD Instance mandatory fields
    private String oldFile;
    private String newFile;
    private GroupedComment commentOld;
    private GroupedComment commentNew;
    private SATDResolution resolution;

    // Commit Metadata for the SATD Instance
    private List<CommitMetaData> initialBlameCommits = new ArrayList<>();
    // Contains only commits that touched the SATD comment
    private List<CommitMetaData> commitsBetweenVersions = new ArrayList<>();
    private CommitMetaData commitAddressed = new NullCommitMetaData();

    public SATDInstance(@NotNull String oldFile, @NotNull String newFile,
                        @NotNull GroupedComment oldComment, @NotNull GroupedComment newComment,
                        @NotNull SATDResolution resolution) {
        this.oldFile = oldFile;
        this.newFile = newFile;
        this.commentOld = oldComment;
        this.commentNew = newComment;
        this.resolution = resolution;
    }

    public SATDResolution getResolution() {
        return resolution;
    }

    public String getCommentNew() {
        if( this.commentNew == null ) {
            return COMMENT_NONE;
        }
        return this.commentNew.getComment();
    }

    public String getOldFile() {
        return this.oldFile;
    }

    public String getNewFile() {
        return this.newFile;
    }

    public String getCommentOld() {
        if( this.commentOld == null ) {
            return COMMENT_NONE;
        }
        return this.commentOld.getComment();
    }

    public GroupedComment getCommentGroupOld() {
        return this.commentOld;
    }

    public GroupedComment getCommentGroupNew() {
        return this.commentNew;
    }

    public int getStartLineNumberOldFile() {
        if( this.commentOld == null ) {
            return 0;
        }
        return this.commentOld.getStartLine();
    }

    public int getEndLineNumberOldFile() {
        if( this.commentOld == null ) {
            return 0;
        }
        return this.commentOld.getEndLine();
    }

    public int getStartLineNumberNewFile() {
        if( this.commentNew == null ) {
            return 0;
        }
        return this.commentNew.getStartLine();
    }

    public int getEndLineNumberNewFile() {
        if( this.commentNew == null ) {
            return 0;
        }
        return this.commentNew.getEndLine();
    }

    public List<CommitMetaData> getInitialBlameCommits() {
        return this.initialBlameCommits;
    }

    public List<CommitMetaData> getCommitsBetweenVersions() {
        return this.commitsBetweenVersions;
    }

    public CommitMetaData getCommitAddressed() {
        return this.commitAddressed;
    }

    public void setResolution(SATDResolution resolution) {
        this.resolution = resolution;
    }

    public enum SATDResolution {
        UNKNOWN,
        FILE_REMOVED,
        FILE_PATH_CHANGED,
        SATD_REMOVED,
        SATD_POSSIBLY_REMOVED,
        SATD_CHANGED,
        SATD_ADDED
    }
}
