package se.rit.edu.satd;

public class SATDInstance {

    public static final String COMMIT_UNKNOWN = "Commit Unknown";
    public static final String FILE_UNKNOWN = "File Unknown";

    private String oldFile;
    private String newFile;
    private String satdComment;

    private String commitAdded = COMMIT_UNKNOWN;
    private String nameOfFileWhenAddressed = FILE_UNKNOWN;

    private String commitRemoved = COMMIT_UNKNOWN;

    private SATDResolution resolution = SATDResolution.UNKNOWN;

    public SATDInstance(String oldFile, String newFile, String satdComment) {
        this.oldFile = oldFile;
        this.newFile = newFile;
        this.satdComment = satdComment;
    }

    public String getCommitAdded() {
        return commitAdded;
    }

    public void setCommitAdded(String commitAdded) {
        this.commitAdded = commitAdded;
    }

    public String getCommitRemoved() {
        return commitRemoved;
    }

    public void setCommitRemoved(String commitRemoved) {
        this.commitRemoved = commitRemoved;
    }

    public String getNameOfFileWhenAddressed() {
        return nameOfFileWhenAddressed;
    }

    public void setNameOfFileWhenAddressed(String nameOfFileWhenAddressed) {
        this.nameOfFileWhenAddressed = nameOfFileWhenAddressed;
    }

    public SATDResolution getResolution() {
        return resolution;
    }

    public void setResolution(SATDResolution resolution) {
        this.resolution = resolution;
    }

    public String getOldFile() {
        return this.oldFile;
    }

    public String getNewFile() {
        return this.newFile;
    }

    public void setNewFile(String newFile) {
        this.newFile = newFile;
    }

    public String getSATDComment() {
        return this.satdComment;
    }

    public String[] toCSV() {
        return new String[] {this.commitAdded, this.commitRemoved, this.oldFile, this.newFile, this.nameOfFileWhenAddressed, this.resolution.name(), satdComment };
    }

    public enum SATDResolution {
        UNKNOWN,
        FILE_REMOVED,
        FILE_RENAMED
    }
}
