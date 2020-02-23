package se.rit.edu.satd;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import se.rit.edu.git.GitUtil;
import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.git.RepositoryInitializer;
import se.rit.edu.satd.detector.SATDDetector;
import se.rit.edu.satd.mining.RepositoryDiffMiner;
import se.rit.edu.satd.writer.OutputWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A class which contains high-level logic for mining SATD Instances from a git repository.
 */
public class SATDMiner {

    // Required fields to be set before SATD can be mined
    private String repositoryURI;
    private SATDDetector satdDetector = null;

    // A reference to the repository initializes. Stored so it can be cleaned
    // once mining has completed
    private RepositoryInitializer repo;

    public SATDMiner(@NotNull String repositoryURI) {
        this.repositoryURI = repositoryURI;
    }

    /**
     * Generates a list of repository references to be used for other mining actions
     * @param mostRecentCommit a boundary in which no future tags will be considered
     *                         -- This is to be used to allow models collection to be replicated
     *                            across different time periods
     * @param sortType the way in which commit should be sorted
     *                 -- The miner will diff release versions as they appear ordered in the list
     *                    so the ordering of the list will significantly impact the output
     * @return a list of repository references at all tags in the repository
     */
    public List<RepositoryCommitReference> getReposAtReleases(String mostRecentCommit, ReleaseSortType sortType) {
        if( repo == null || !repo.didInitialize() ) {
            if( !this.initializeRepo() ) {
                System.err.println("Repository failed to initialize");
                return new ArrayList<>();
            }
        }
        List<RepositoryCommitReference> refs =  this.repo.getComparableRepositories(mostRecentCommit);
        if( sortType == ReleaseSortType.CHRONOLOGICAL ) {
            return refs;
        }
        return refs;
    }

    public List<RepositoryCommitReference> getReposAtReleases(ReleaseSortType sortType) {
        return getReposAtReleases(null, sortType);
    }

    public void setSatdDetector(SATDDetector detector) {
        this.satdDetector = detector;
    }

    /**
     * Iterates over all supplied commits, and outputs a difference in SATD occurrences between
     * each adjacent commit reference in commitRefs
     * @param commitRefs a list of supplied commit references to be diffed for SATD
     * @param writer an OutputWriter that will handle the output of the miner
     */
    public void writeRepoSATD(List<RepositoryCommitReference> commitRefs, OutputWriter writer) {
        // Check miner configured correctly
        if( this.satdDetector == null ) {
            System.err.println("Miner does not have SATD Detector set. Please call setSatdDetector() on the Miner object.");
            return;
        }
        // Go through each commit, and diff against the adjacent commits
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

    /**
     * Cleans the repository that was mined by the Miner. This should delete all files created
     * by the miner.
     */
    public void cleanRepo() {
        this.repo.cleanRepo();
        try {
            FileUtils.deleteDirectory(new File(repo.getRepoDir()));
        } catch (IOException e) {
            System.err.println("Error in deleting cleaned git repo.");
        }
    }

    private boolean initializeRepo() {
        this.repo = new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGithubURI(this.repositoryURI));
        return repo.initRepo();
    }

    // TODO can we sort the repos based on chronological vs. Some kind of release parsing?
    public enum ReleaseSortType {
        CHRONOLOGICAL,
        RELEASE_PARSE
    }
}
