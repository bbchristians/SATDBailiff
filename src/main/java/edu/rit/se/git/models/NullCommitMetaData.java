package edu.rit.se.git.models;

/**
 * Null instance for the CommitMetaData class, to be used in place of null commits
 */
public class NullCommitMetaData extends CommitMetaData {

    public NullCommitMetaData() {
        super();
    }

    @Override
    public String getHash() {
        return "None";
    }

    @Override
    public String getAuthorName() {
        return "None";
    }

    @Override
    public String getAuthorEmail() {
        return "None";
    }

    @Override
    public String getCommitterName() {
        return "None";
    }

    @Override
    public String getCommitterEmail() {
        return "None";
    }
}
