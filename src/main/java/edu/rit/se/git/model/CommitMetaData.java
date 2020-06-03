package edu.rit.se.git.model;

import lombok.Getter;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Date;

import static org.eclipse.jgit.diff.DiffEntry.DEV_NULL;

/**
 * Data class for diff metadata
 */
public class CommitMetaData {

    @Getter
    private String hash;

    @Getter
    private String authorName;
    @Getter
    private String authorEmail;
    @Getter
    private Date authorDate;

    @Getter
    private String committerName;
    @Getter
    private String committerEmail;
    @Getter
    private Date commitDate;

    /**
     * Constructor to generate a CommitMetaData object from a
     * JGit RevCommit object
     * @param commit a JGit RevCommit object
     */
    public CommitMetaData(RevCommit commit) {
        if( commit != null ) {
            this.hash = commit.getName();
            this.authorName = commit.getAuthorIdent().getName();
            this.authorEmail = commit.getAuthorIdent().getEmailAddress();
            this.authorDate = commit.getAuthorIdent().getWhen();
            this.committerName = commit.getCommitterIdent().getName();
            this.committerEmail = commit.getCommitterIdent().getEmailAddress();
            this.commitDate = commit.getCommitterIdent().getWhen();
        } else {
            this.hash = DEV_NULL;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CommitMetaData &&
                this.hash != null &&
                this.hash.equals(((CommitMetaData) o).hash);
    }
}
