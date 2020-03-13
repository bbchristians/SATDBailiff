package edu.rit.se.satd;

import edu.rit.se.git.GitUtil;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.git.RepositoryInitializer;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.RepositoryDiffMiner;
import edu.rit.se.satd.writer.OutputWriter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * A class which contains high-level logic for mining SATD Instances from a git repository.
 */
@RequiredArgsConstructor
public class SATDMiner {

    @NonNull
    private String repositoryURI;
    @NonNull
    private SATDDetector satdDetector;

    // A reference to the repository initializes. Stored so it can be cleaned
    // once mining has completed
    private RepositoryInitializer repo;

    // A list of the diffs that have already been accounted for to prevent
    // infinite recursion
    // Should be populated with results from this.getMultiRefHash()
    private Set<String> alreadyDiffedCommits = new HashSet<>();

    public RepositoryCommitReference getBaseCommit(String head) {
        if( (repo == null || !repo.didInitialize()) && !this.initializeRepo() ) {
            System.err.println("Repository failed to initialize");
            return null;
        }
        return this.repo.getMostRecentCommit(head);
    }

    /**
     * Iterates over all supplied commits, and outputs a difference in SATD occurrences between
     * each adjacent commit reference in commitRefs
     * @param commitRef a list of supplied commit references to be diffed for SATD
     * @param writer an OutputWriter that will handle the output of the miner
     */
    public void writeRepoSATD(RepositoryCommitReference commitRef, OutputWriter writer) {
        if( commitRef == null ) {
            return;
        }
        // Go through each commit, and diff against the adjacent commits
        commitRef.getParentCommitReferences().stream()
                // Filter out the already diffed commits
                .filter(parentRef -> !this.alreadyDiffedCommits.contains(getMultiRefHash(commitRef, parentRef)))
                .peek(parentRef -> this.alreadyDiffedCommits.add(getMultiRefHash(commitRef, parentRef)))
                // Recurse
                .peek(parentRef -> writeRepoSATD(parentRef, writer))
                .map(parentRef -> new RepositoryDiffMiner(parentRef, commitRef, this.satdDetector))
                .map(RepositoryDiffMiner::mineDiff)
                .forEach(diff -> {
                    try {
                        writer.writeDiff(diff);
                    } catch (IOException e) {
                        System.err.println("Error writing diff!");
                    }
                });
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
            e.printStackTrace();
        }
    }

    private boolean initializeRepo() {
        this.repo = new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGithubURI(this.repositoryURI));
        return this.repo.initRepo();
    }

    /**
     * Generates a unique hash based off the two given repositories
     * @param r1 a repository reference
     * @param r2 a repository reference
     * @return A unique hash for two compared repositories
     */
    private String getMultiRefHash(RepositoryCommitReference r1, RepositoryCommitReference r2) {
        return r1.getCommit().getName() + r2.getCommit().getName();
    }
}
