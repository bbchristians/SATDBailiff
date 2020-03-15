package edu.rit.se.satd;

import edu.rit.se.satd.comment.GroupedComment;
import lombok.AllArgsConstructor;
import lombok.Getter;


/**
 * Data class which stores information about the SATD Instance
 */
@AllArgsConstructor
public class SATDInstance {

    // File paths
    @Getter
    private String oldFile;
    @Getter
    private String newFile;
    // Comment objects
    @Getter
    private GroupedComment commentOld;
    @Getter
    private GroupedComment commentNew;
    // Resolution
    @Getter
    private SATDResolution resolution;

    public int getStartLineNumberOldFile() {
        return this.commentOld.getStartLine();
    }

    public int getEndLineNumberOldFile() {
        return this.commentOld.getEndLine();
    }

    public int getStartLineNumberNewFile() {
        return this.commentNew.getStartLine();
    }

    public int getEndLineNumberNewFile() {
        return this.commentNew.getEndLine();
    }

    public enum SATDResolution {
        FILE_REMOVED,
        FILE_PATH_CHANGED,
        SATD_REMOVED,
        SATD_CHANGED,
        SATD_ADDED
    }
}
