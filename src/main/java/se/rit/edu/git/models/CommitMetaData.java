package se.rit.edu.git.models;

import org.eclipse.jgit.revwalk.RevCommit;

public class CommitMetaData {

    private String hash;

    private String authorName;
    private String authorEmail;

    private String committerName;
    private String committerEmail;

    // For inheritance
    CommitMetaData() {}

    public CommitMetaData(RevCommit commit) {
        this.hash = commit.getName();
        this.authorName = commit.getAuthorIdent().getName();
        this.authorEmail = commit.getAuthorIdent().getEmailAddress();
        this.committerName = commit.getCommitterIdent().getName();
        this.committerEmail = commit.getCommitterIdent().getEmailAddress();
    }

    public String getHash() {
        return this.hash;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public String getCommitterName() {
        return committerName;
    }

    public String getCommitterEmail() {
        return committerEmail;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CommitMetaData &&
                this.hash != null &&
                this.hash.equals(((CommitMetaData) o).hash);
    }
}
