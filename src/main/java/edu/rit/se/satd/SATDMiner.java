package edu.rit.se.satd;

import edu.rit.se.git.GitUtil;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.git.RepositoryInitializer;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.MinerStatus;
import edu.rit.se.satd.mining.RepositoryDiffMiner;
import edu.rit.se.satd.writer.OutputWriter;
import edu.rit.se.util.ElapsedTimer;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class which contains high-level logic for mining SATD Instances from a git repository.
 */
public class SATDMiner {

    @NonNull
    private String repositoryURI;
    @NonNull
    private SATDDetector satdDetector;

    // A reference to the repository initializes. Stored so it can be cleaned
    // once mining has completed
    private RepositoryInitializer repo;

    // Miner status for console output
    private MinerStatus status;

    private ElapsedTimer timer = new ElapsedTimer();

    public SATDMiner(String repositoryURI, SATDDetector satdDetector) {
        this.repositoryURI = repositoryURI;
        this.satdDetector = satdDetector;
        this.status = new MinerStatus(GitUtil.getRepoNameFromGithubURI(this.repositoryURI));
    }

    public void disableStatusOutput() {
        this.status.setOutputEnabled(false);
    }

    public RepositoryCommitReference getBaseCommit(String head) {
        this.timer.start();
        this.status.beginInitialization();
        if( (repo == null || !repo.didInitialize()) && !this.initializeRepo() ) {
            System.err.println("Repository failed to initialize");
            return null;
        }
        return this.repo.getMostRecentCommit(head);
    }

    /**
     * Cleans the repository that was mined by the Miner. This should delete all files created
     * by the miner.
     */
    public void cleanRepo() {
        this.status.beginCleanup();
        this.repo.cleanRepo();
        try {
            // Two files are created, so delete the parent as well
            FileUtils.deleteDirectory(new File(repo.getRepoDir()).getParentFile());
        } catch (IOException e) {
            System.err.println("Error in deleting cleaned git repo.");
            e.printStackTrace();
        }
        this.timer.end();
        this.status.setComplete(this.timer.readMS());
    }

    /**
     * Iterates over all supplied commits, and outputs a difference in SATD occurrences between
     * each adjacent commit reference in commitRefs
     * @param commitRef a list of supplied commit references to be diffed for SATD
     * @param writer an OutputWriter that will handle the output of the miner
     */
    public void writeRepoSATD(RepositoryCommitReference commitRef, OutputWriter writer) {
        if( commitRef == null ) {
            this.status.setError();
            return;
        }
        this.status.beginCalculatingDiffs();

        final Set<DiffPair> allDiffPairs =  this.getAllDiffPairs(commitRef);

        this.status.beginMiningSATD();
        this.status.setNDiffsPromised(allDiffPairs.size());

        allDiffPairs.stream()
                .map(pair -> new RepositoryDiffMiner(pair.parentRepo, pair.repo, this.satdDetector))
                .map(repositoryDiffMiner -> {
                    // Output
                    this.status.setDisplayWindow(repositoryDiffMiner.getDiffString());
                    return repositoryDiffMiner.mineDiff();
                })
                .forEach(diff -> {
                    try {
                        writer.writeDiff(diff);
                        this.status.fulfilDiffPromise();
                    } catch (IOException e) {
                        System.err.println("Error writing diff!");
                    }
                });

    }

    private boolean initializeRepo() {
        this.repo = new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGithubURI(this.repositoryURI));
        return this.repo.initRepo();
    }

    private Set<DiffPair> getAllDiffPairs(RepositoryCommitReference curRef) {
        Set<RepositoryCommitReference> visitedCommits = new HashSet<>();
        Set<RepositoryCommitReference> allCommits = new HashSet<>();
        allCommits.add(curRef);
        // Continue until no new commit refs are found
        while( allCommits.size() > visitedCommits.size() ) {
            allCommits.addAll(
                    allCommits.stream()
                            .filter(ref -> !visitedCommits.contains(ref))
                            .peek(visitedCommits::add)
                            .map(RepositoryCommitReference::getParentCommitReferences)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toSet())
            );
        }
        return allCommits.stream()
                .flatMap(ref ->
                        ref.getParentCommitReferences().stream()
                                .map(parent -> new DiffPair(ref, parent)
                        )
                )
                .collect(Collectors.toSet());

    }

    @RequiredArgsConstructor
    private class DiffPair {

        @NonNull
        @Getter
        private RepositoryCommitReference repo;
        @NonNull
        @Getter
        private RepositoryCommitReference parentRepo;

        @Override
        public boolean equals(Object obj) {
            if( obj instanceof DiffPair ) {
                return this.repo.getCommit().getName().equals(((DiffPair) obj).repo.getCommit().getName()) &&
                        this.parentRepo.getCommit().getName().equals(((DiffPair) obj).parentRepo.getCommit().getName());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.parentRepo.getCommit().getName() + this.repo.getCommit().getName()).hashCode();
        }
    }
}
