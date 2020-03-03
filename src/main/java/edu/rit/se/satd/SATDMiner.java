package edu.rit.se.satd;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import edu.rit.se.git.GitUtil;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.git.RepositoryInitializer;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.RepositoryDiffMiner;
import edu.rit.se.satd.writer.OutputWriter;

import java.io.File;
import java.io.IOException;

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

    public RepositoryCommitReference getBaseCommit(String mostRecentCommit) {
        if( (repo == null || !repo.didInitialize()) && !this.initializeRepo() ) {
            System.err.println("Repository failed to initialize");
            return null;
        }
        return this.repo.getMostRecentCommit(mostRecentCommit);
    }

    public void setSatdDetector(SATDDetector detector) {
        this.satdDetector = detector;
    }

    /**
     * Iterates over all supplied commits, and outputs a difference in SATD occurrences between
     * each adjacent commit reference in commitRefs
     * @param commitRef a list of supplied commit references to be diffed for SATD
     * @param writer an OutputWriter that will handle the output of the miner
     */
    public void writeRepoSATD(RepositoryCommitReference commitRef, OutputWriter writer) {
        // Check miner configured correctly
        if( this.satdDetector == null ) {
            System.err.println("Miner does not have SATD Detector set. Please call setSatdDetector() on the Miner object.");
            return;
        }

        if( commitRef == null ) {
            return;
        }
        // Go through each commit, and diff against the adjacent commits
        commitRef.getParentCommitReferences().stream().map(RepositoryDiffMiner::ofFirstRepository)
                .map(r -> r.andSecondRepository(commitRef))
                .map(r -> r.usingDetector(this.satdDetector))
                .map(RepositoryDiffMiner::mineDiff)
                .forEach(diff -> {
                    try {
                        writer.writeDiff(diff);
                    } catch (IOException e) {
                        System.err.println("Error writing diff!");
                    }
                });

        // Recurse on parents
        commitRef.getParentCommitReferences().forEach(p -> writeRepoSATD(p, writer));

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
        return this.repo.initRepo();
    }
}
