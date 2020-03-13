package edu.rit.se.git.commit;

import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Date;

/**
 * Data class for commit metadata
 */
public class CommitMetaData {

    private String hash;

    private String authorName;
    private String authorEmail;
    private Date authorDate;

    private String committerName;
    private String committerEmail;
    private Date commitDate;

    // For inheritance
    CommitMetaData() {}

    public CommitMetaData(RevCommit commit) {
        this.hash = commit.getName();
        this.authorName = commit.getAuthorIdent().getName();
        this.authorEmail = commit.getAuthorIdent().getEmailAddress();
        this.authorDate = commit.getAuthorIdent().getWhen();
        this.committerName = commit.getCommitterIdent().getName();
        this.committerEmail = commit.getCommitterIdent().getEmailAddress();
        this.commitDate = commit.getCommitterIdent().getWhen();
    }

    public String getHash() {
        return this.hash;
    }

    public String getAuthorName() {
        return this.authorName;
    }

    public String getAuthorEmail() {
        return this.authorEmail;
    }

    public Date getAuthorDate() {
        return this.authorDate;
    }

    public String getCommitterName() {
        return this.committerName;
    }

    public String getCommitterEmail() {
        return this.committerEmail;
    }

    public Date getCommitDate() {
        return this.commitDate;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CommitMetaData &&
                this.hash != null &&
                this.hash.equals(((CommitMetaData) o).hash);
    }
}
