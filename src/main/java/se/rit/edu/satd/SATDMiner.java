package se.rit.edu.satd;

import se.rit.edu.git.GitUtil;
import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.git.RepositoryInitializer;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
    }

    private void initializeRepo() {
        this.repo = new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGitURI(this.repositoryURI));
    }

    public enum ReleaseSortType {
        CHRONOLOGICAL,
        RELEASE_PARSE
    }
}
