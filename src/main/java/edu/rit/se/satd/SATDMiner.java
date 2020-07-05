package edu.rit.se.satd;

import edu.rit.se.git.DevNullCommitReference;
import edu.rit.se.git.GitUtil;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.git.RepositoryInitializer;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.RepositoryDiffMiner;
import edu.rit.se.satd.mining.ui.ElapsedTimer;
import edu.rit.se.satd.mining.ui.MinerStatus;
import edu.rit.se.satd.model.SATDDifference;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.model.SATDInstanceInFile;
import edu.rit.se.satd.writer.OutputWriter;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class which contains high-level logic for mining SATD Instances from a git repository.
 */
public class SATDMiner {

    @NonNull
    private String repositoryURI;
    @NonNull
    private SATDDetector satdDetector;

    @Setter
    private String githubUsername = null;
    @Setter
    private String githubPassword = null;

    // A reference to the repository initializes. Stored so it can be cleaned
    // once mining has completed
    private RepositoryInitializer repo;

    // Miner status for console output
    private MinerStatus status;

    private Map<SATDInstanceInFile, Integer> satdInstanceMappings = new HashMap<>();

    private ElapsedTimer timer = new ElapsedTimer();

    private int curSATDId;

    @Getter
    private static boolean errorOutputEnabled = true;

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

    public static void disableErrorOutput() {
        errorOutputEnabled = false;
    }

    public RepositoryCommitReference getBaseCommit(String head) {
        this.timer.start();
        this.status.beginInitialization();
        if( (repo == null || !repo.didInitialize()) &&
                !this.initializeRepo(this.githubUsername, this.githubPassword) ) {
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
     * each adjacent diff reference in commitRefs
     * @param commitRef a list of supplied diff references to be diffed for SATD
     * @param writer an OutputWriter that will handle the output of the miner
     */
    public void writeRepoSATD(RepositoryCommitReference commitRef, OutputWriter writer) {
        if( commitRef == null ) {
            this.status.setError();
            return;
        }
        this.status.beginCalculatingDiffs();

        final List<DiffPair> allDiffPairs =  this.getAllDiffPairs(commitRef);

        this.status.beginMiningSATD();
        this.status.setNDiffsPromised(allDiffPairs.size());

        allDiffPairs.stream()
                .map(pair -> new RepositoryDiffMiner(pair.parentRepo, pair.repo, this.satdDetector))
                .map(repositoryDiffMiner -> {
                    this.status.setDisplayWindow(repositoryDiffMiner.getDiffString());
                    return repositoryDiffMiner.mineDiff();
                })
                .map(this::mapInstancesInDiffToPriorInstances)
                .forEach(diff -> {
                    try {
                        writer.writeDiff(diff);
                        this.status.fulfilDiffPromise();
                    } catch (IOException e) {
                        this.status.addErrorEncountered();
                        System.err.println("Error writing diff: " + e.getLocalizedMessage());
                    }
                });

    }

    private boolean initializeRepo(String username, String password) {
        this.repo = ( username != null && password != null ) ?
                new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGithubURI(this.repositoryURI),
                        username, password):
                new RepositoryInitializer(this.repositoryURI, GitUtil.getRepoNameFromGithubURI(this.repositoryURI));
        return this.repo.initRepo();
    }

    private List<DiffPair> getAllDiffPairs(RepositoryCommitReference curRef) {
        final Set<RepositoryCommitReference> visitedCommits = new HashSet<>();
        final Set<RepositoryCommitReference> allCommits = new HashSet<>();
        allCommits.add(curRef);
        // Continue until no new diff refs are found
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
                // Only include non-merge commits
                .filter(ref -> ref.getParentCommitReferences().size() < 2)
                .flatMap(ref -> {
                    if( ref.getParentCommitReferences().isEmpty() ) {
                        return Stream.of(new DiffPair(ref, new DevNullCommitReference()));
                    } else {
                        return ref.getParentCommitReferences().stream()
                                .map(parent -> new DiffPair(ref, parent));
                    }
                })
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Associates all SATDInstances in the diff object with other instances found
     * in this project and removes duplicate entries
     * @param diff an SATDDifference object
     * @return the SATDDifference object
     */
    private SATDDifference mapInstancesInDiffToPriorInstances(SATDDifference diff) {
        return diff.usingNewInstances(
                diff.getSatdInstances().stream()
                        .distinct()
                        .map(this::mapInstanceToNewInstanceId)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Maps an SATD instance to a previously located instance ID if one can be found.
     * If one is not found, then a new ID is generated where applicable.
     * @param satdInstance an SATD Instance
     * @return the SATD Instance
     */
    private SATDInstance mapInstanceToNewInstanceId(SATDInstance satdInstance) {
        switch (satdInstance.getResolution()) {
            case SATD_ADDED:
                // SATD was added, so we know it won't relate to other instances
                // It could possibly be duplicated from another instance, but detecting
                // that is currently out of scope for this tool.
                if( !this.satdInstanceMappings.containsKey(satdInstance.getNewInstance()) ) {
                    this.satdInstanceMappings.put(satdInstance.getNewInstance(), this.getNewSATDId());
                } else {
                    if( isErrorOutputEnabled() ) {
                        System.err.println("\nMultiple SATD_ADDED instances for " +
                                satdInstance.getOldInstance().toString());
                    }
                    this.status.addErrorEncountered();
                }
                satdInstance.setId(this.satdInstanceMappings.get(satdInstance.getNewInstance()));
                break;
            case SATD_CHANGED: case FILE_PATH_CHANGED: case CLASS_OR_METHOD_CHANGED:
                // SATD was changed from the previous version, so update it here
                if( !this.satdInstanceMappings.containsKey(satdInstance.getOldInstance()) ) {
                    // Looks like we cannot find the old SATD Instance for whatever reason
                    // This is not a case which should be hit
                    if( isErrorOutputEnabled() ) {
                        System.err.println("\nCould not get satd_instance_id for " +
                                satdInstance.getOldInstance().toString());
                    }
                    this.status.addErrorEncountered();
                    this.satdInstanceMappings.put(satdInstance.getNewInstance(), this.getNewSATDId());
                } else {
                    // Otherwise it exists, so we can propagate it forward
                    this.satdInstanceMappings.put(satdInstance.getNewInstance(),
                            this.satdInstanceMappings.get(satdInstance.getOldInstance()));
                }
                satdInstance.setId(this.satdInstanceMappings.get(satdInstance.getNewInstance()));
                break;
            case SATD_REMOVED: case FILE_REMOVED: case SATD_MOVED_FILE:
                // SATD was removed from the project, but the satdInstance still needs to have
                // its ID set if possible
                if( !this.satdInstanceMappings.containsKey(satdInstance.getOldInstance()) ) {
                    // Looks like we cannot find the old SATD Instance for whatever reason
                    // This is not a case which should be hit
                    if( isErrorOutputEnabled() ) {
                        System.err.println("\nCould not get satd_instance_id for " +
                                satdInstance.getOldInstance().toString());
                    }
                    this.status.addErrorEncountered();
                    satdInstance.setId(this.getNewSATDId());
                } else {
                    // A previous instance can be associated and was removed.
                    // However, it could have been moved to another file.
                    if( satdInstance.getResolution().equals(SATDInstance.SATDResolution.SATD_MOVED_FILE) ) {
                        // It was moved to another file, so propagate the new instance forward
                        // We cannot remove it from the list because it may have propagated multiple times
                        // FIXME - we should store that the instance was removed and remove it after all
                        //  cases from this propagation have been processed
                        satdInstance.setParentId(this.satdInstanceMappings.get(satdInstance.getOldInstance()));
                        satdInstance.setId(this.getNewSATDId());
                        this.satdInstanceMappings.put(satdInstance.getNewInstance(), satdInstance.getId());
                    } else {
                        // It was not moved to another file, so we can kill the instance here.
                        satdInstance.setId(this.satdInstanceMappings.get(satdInstance.getOldInstance()));
                        this.satdInstanceMappings.remove(satdInstance.getOldInstance());
                    }
                }
                break;
        }
        return satdInstance;
    }

    private int getNewSATDId() {
        return ++this.curSATDId;
    }

    @RequiredArgsConstructor
    public class DiffPair implements Comparable {

        @NonNull
        @Getter
        private RepositoryCommitReference repo;
        @NonNull
        @Getter
        private RepositoryCommitReference parentRepo;

        @Override
        public boolean equals(Object obj) {
            if( obj instanceof DiffPair ) {
                return this.repo.getCommitHash().equals(((DiffPair) obj).repo.getCommitHash()) &&
                        this.parentRepo.getCommitHash().equals(((DiffPair) obj).parentRepo.getCommitHash());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (this.parentRepo.getCommitHash() + this.repo.getCommitHash()).hashCode();
        }

        @Override
        public int compareTo(Object o) {
            if( o instanceof DiffPair ) {
                int commitTimeDiff = this.repo.getCommitTime() - ((DiffPair) o).repo.getCommitTime();
                // If the commits were committed at the same time, look at the authored date
                // to determine which came first
                if( commitTimeDiff == 0 ) {
                    return Long.compare(this.repo.getAuthoredTime(), ((DiffPair) o).repo.getAuthoredTime());
                }
                return commitTimeDiff;
            }
            return -1;
        }
    }
}
