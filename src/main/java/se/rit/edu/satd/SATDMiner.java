package se.rit.edu.satd;

import org.apache.commons.io.FileUtils;
import se.rit.edu.git.GitUtil;
import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.git.RepositoryInitializer;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class SATDMiner {

    private String repositoryURI;
    private RepositoryInitializer repo;

    public SATDMiner(String repositoryURI) {
        this.repositoryURI = repositoryURI;
    }

    public List<RepositoryCommitReference> getReposAtReleases(String mostRecentCommit, ReleaseSortType sortType) {
        if( repo == null ) {
            this.initializeRepo();
        }
        List<RepositoryCommitReference> refs =  this.repo.getComparableRepositories(mostRecentCommit);
        if( sortType == ReleaseSortType.CHRONOLOGICAL ) {
            return refs;
        }
        // TODO is there a way to sort the refs by the release rather than chronologically?
        return refs;
    }

    public List<RepositoryCommitReference> getReposAtReleases(ReleaseSortType sortType) {
        return getReposAtReleases(null, sortType);
    }

    public void cleanRepo() {
        this.repo.cleanRepo();
        try {
            FileUtils.deleteDirectory(new File(repo.getRepoDir()));
        } catch (IOException e) {
            System.err.println("Error in deleting cleaned git repo.");
        }
    }

    private void initializeRepo() {
        this.repo = new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGitURI(this.repositoryURI));
    }

    // TODO can we sort the repos based on chronological vs. Some kind of release parsing?
    public enum ReleaseSortType {
        CHRONOLOGICAL,
        RELEASE_PARSE
    }
}
