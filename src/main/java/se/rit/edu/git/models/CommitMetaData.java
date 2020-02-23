package se.rit.edu.git.models;

import org.eclipse.jgit.revwalk.RevCommit;

public class CommitMetaData {

    private String hash;

    CommitMetaData() {}

    public CommitMetaData(RevCommit commit) {
        this.hash = commit.getName();
    }

    public String getHash() {
        return this.hash;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CommitMetaData && this.hash.equals(((CommitMetaData) o).hash);
    }
}
