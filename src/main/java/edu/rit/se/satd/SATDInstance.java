package edu.rit.se.satd;

import edu.rit.se.satd.comment.GroupedComment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;


/**
 * Data class which stores information about the SATD Instance
 */
@RequiredArgsConstructor
public class SATDInstance {

    // File paths
    @Getter
    final private String oldFile;
    @Getter
    final private String newFile;
    // Comment objects
    @Getter
    final private GroupedComment commentOld;
    @Getter
    final private GroupedComment commentNew;
    // Resolution
    @Getter
    final private SATDResolution resolution;
    // SATD ID to associate with other instances.
    // Two SATDInstances with the same ID stem from the same SATD Instance
    @Getter
    @Setter
    private int id = -1;

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
