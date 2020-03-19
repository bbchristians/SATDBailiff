package edu.rit.se.satd.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;


/**
 * Data class which stores information about the SATD Instance
 */
@RequiredArgsConstructor
public class SATDInstance {

    @Getter
    final private SATDInstanceInFile oldInstance;

    @Getter
    final private SATDInstanceInFile newInstance;

    // Resolution
    @Getter
    final private SATDResolution resolution;
    // SATD ID to associate with other instances.
    // Two SATDInstances with the same ID stem from the same SATD Instance
    @Getter
    @Setter
    private int id = -1;

    public int getStartLineNumberOldFile() {
        return this.oldInstance.getComment().getStartLine();
    }

    public int getEndLineNumberOldFile() {
        return this.oldInstance.getComment().getEndLine();
    }

    public int getStartLineNumberNewFile() {
        return this.newInstance.getComment().getStartLine();
    }

    public int getEndLineNumberNewFile() {
        return this.newInstance.getComment().getEndLine();
    }

    public enum SATDResolution {
        FILE_REMOVED,
        FILE_PATH_CHANGED,
        SATD_REMOVED,
        SATD_CHANGED,
        SATD_ADDED,
        CLASS_OR_METHOD_CHANGED
    }
}
