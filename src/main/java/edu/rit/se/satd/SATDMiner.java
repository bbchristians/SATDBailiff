package edu.rit.se.satd;

import edu.rit.se.git.GitUtil;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.git.RepositoryInitializer;
import edu.rit.se.satd.comment.GroupedComment;
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
import java.util.*;
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

    private Map<GroupedComment, Integer> satdInstanceMappings = new HashMap<>();

    private ElapsedTimer timer = new ElapsedTimer();

    private int curSATDId;

    public SATDMiner(String repositoryURI, SATDDetector satdDetector) {
        this.repositoryURI = repositoryURI;
        this.satdDetector = satdDetector;
        this.status = new MinerStatus(GitUtil.getRepoNameFromGithubURI(this.repositoryURI));
        // Start the SATD ID incrementing on a unique value for each repository
        this.curSATDId = this.repositoryURI.hashCode();
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
                .sorted()
                .map(pair -> new RepositoryDiffMiner(pair.parentRepo, pair.repo, this.satdDetector))
                .map(repositoryDiffMiner -> {
                    // Output
                    this.status.setDisplayWindow(repositoryDiffMiner.getDiffString());
                    return repositoryDiffMiner.mineDiff();
                })
                .map(this::mapSATDInstanceLikeness)
                .forEach(diff -> {
                    try {
                        writer.writeDiff(diff);
                        this.status.fulfilDiffPromise();
                    } catch (IOException e) {
                        System.err.println("Error writing diff: " + e.getLocalizedMessage());
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

    /**
     * Associates all SATDInstances in the diff object with other instances found
     * in this project
     * @param diff a SATDDifference object
     * @return the SATDDifference object
     * TODO what can be done about instances with the same text, in the same method and class??
     */
    private SATDDifference mapSATDInstanceLikeness(SATDDifference diff) {
        diff.getSatdInstances().forEach(satdInstance -> {
            switch (satdInstance.getResolution()) {
                case SATD_ADDED:
                    // SATD was added, so we know it wont relate to other instances
                    // It could possibly be duplicated from another instance, but detecting
                    // that is currently out of scope for this tool.
                    if( !this.satdInstanceMappings.containsKey(satdInstance.getCommentNew()) ) {
                        this.satdInstanceMappings.put(satdInstance.getCommentNew(), this.getNewSATDId());
                    }
                    satdInstance.setId(this.satdInstanceMappings.get(satdInstance.getCommentNew()));
                    break;
                case SATD_CHANGED: case FILE_PATH_CHANGED:
                    // SATD was changed from the previous version, so update it here
                    // TODO does this run into issues if the class or method name is changed?
                    //  -- a case which the tool currently does not account for
                    if( !this.satdInstanceMappings.containsKey(satdInstance.getCommentOld()) ) {
                        // Looks like we cannot find the old SATD Instance for whatever reason,
                        // so make a new one
                        this.satdInstanceMappings.put(satdInstance.getCommentNew(), this.getNewSATDId());
                    } else {
                        // Otherwise it exists, so we can propagate it forward
                        this.satdInstanceMappings.put(satdInstance.getCommentNew(),
                                this.satdInstanceMappings.get(satdInstance.getCommentOld()));
                    }
                    satdInstance.setId(this.satdInstanceMappings.get(satdInstance.getCommentNew()));
                    break;
                case SATD_REMOVED: case FILE_REMOVED:
                    // SATD was removed from the project, but the satdInstance still needs to have
                    // its ID set if possible
                    if( !this.satdInstanceMappings.containsKey(satdInstance.getCommentOld()) ) {
                        // Looks like we cannot find the old SATD Instance for whatever reason
                        // This is not a case which should be hit
                        // TODO does this run into issues if the class or method name is changed?
                        //  -- a case which the tool currently does not account for
                        System.err.println("Detected that an SATD Instance was removed without ever " +
                                "having been added! This should not happen!");
                        satdInstance.setId(this.getNewSATDId());
                    } else {
                        // Otherwise it exists, so we can propagate it forward
                        satdInstance.setId(this.satdInstanceMappings.get(satdInstance.getCommentOld()));
                    }
                    break;
            }
        });
        return diff;
    }

    private int getNewSATDId() {
        return ++this.curSATDId;
    }

    @RequiredArgsConstructor
    private class DiffPair implements Comparable {

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

        @Override
        public int compareTo(Object o) {
            if( o instanceof DiffPair ) {
                return this.repo.getCommit().getCommitTime() - ((DiffPair) o).repo.getCommit().getCommitTime();
            }
            return -1;
        }
    }
}
