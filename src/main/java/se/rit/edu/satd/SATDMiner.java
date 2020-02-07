package se.rit.edu.satd;

import org.eclipse.jgit.lib.Repository;
import se.rit.edu.git.GitUtil;
import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.git.RepositoryInitializer;

import java.util.List;

public class SATDMiner {

    private String repositoryURI;
    private RepositoryInitializer repo;

    public SATDMiner(String repositoryURI) {
        this.repositoryURI = repositoryURI;
    }

    public List<RepositoryCommitReference> getTaggedCommits(int everyN, String mostRecentCommit) {
        if( repo == null ) {
            this.initializeRepo();
        }
        return this.repo.getComparableRepositories(mostRecentCommit, everyN);
    }

    public List<RepositoryCommitReference> getTaggedCommits(int everyN) {
        return getTaggedCommits(everyN, null);
    }

    public void cleanRepo() {

    }

    private void initializeRepo() {
        this.repo = new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGitURI(this.repositoryURI));
    }
}
