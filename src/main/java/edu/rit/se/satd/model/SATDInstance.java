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
    // SATD Duplication ID to differentiate instances if they
    // align in all other ways
    @Getter
    @Setter
    private int duplicationId = 0;

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

    @Override
    public boolean equals(Object obj) {
        if( obj instanceof SATDInstance ) {
            return this.oldInstance.equals(((SATDInstance) obj).oldInstance) &&
                    this.newInstance.equals(((SATDInstance) obj).newInstance) &&
                    this.resolution.equals(((SATDInstance) obj).resolution) &&
                    this.duplicationId == ((SATDInstance) obj).duplicationId;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.oldInstance.hashCode() +
                this.newInstance.hashCode() +
                this.resolution.hashCode() +
                this.duplicationId;
    }
}
