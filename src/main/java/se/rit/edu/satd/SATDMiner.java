package se.rit.edu.satd;

import org.apache.commons.io.FileUtils;
import se.rit.edu.git.GitUtil;
import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.git.RepositoryInitializer;
import se.rit.edu.satd.detector.SATDDetector;
import se.rit.edu.satd.mining.RepositoryDiffMiner;
import se.rit.edu.satd.writer.OutputWriter;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class SATDMiner {

    private String repositoryURI;
    private RepositoryInitializer repo;

    private SATDDetector satdDetector = null;

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

    public void setSatdDetector(SATDDetector detector) {
        this.satdDetector = detector;
    }

    public void writeRepoSATD(List<RepositoryCommitReference> commitRefs, OutputWriter writer) {
        if( this.satdDetector == null ) {
            System.err.println("Miner does not have SATD Detector set. Please call setSatdDetector() on the Miner object.");
            return;
        }
        for (int i = 1; i < commitRefs.size(); i++) {
            SATDDifference diff = RepositoryDiffMiner.ofFirstRepository(commitRefs.get(i-1))
                                        .andSecondRepository(commitRefs.get(i))
                                        .usingDetector(this.satdDetector)
                                        .mineDiff();
            try {
                writer.writeDiff(diff);
            } catch (IOException e) {
                System.err.println("IOException while writing diff: " + e.getMessage());
                e.printStackTrace();
            }
        }
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
