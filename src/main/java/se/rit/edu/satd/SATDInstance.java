package se.rit.edu.satd;

public class SATDInstance {

    private String oldFile;
    private String newFile;
    private String satdComment;

    public SATDInstance(String oldFile, String newFile, String satdComment) {
        this.oldFile = oldFile;
        this.newFile = newFile;
        this.satdComment = satdComment;
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

    public String[] toCSV() {
        return new String[] {this.oldFile, this.newFile, satdComment };
    }
}
