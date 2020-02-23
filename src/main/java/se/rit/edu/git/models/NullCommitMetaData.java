package se.rit.edu.git.models;

public class NullCommitMetaData extends CommitMetaData {

    public NullCommitMetaData() {
        super();
    }

    @Override
    public String getHash() {
        return "None";
    }
}
