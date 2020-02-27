package se.rit.edu.satd.mining;

import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.git.RepositoryFileMapping;
import se.rit.edu.git.commitlocator.*;
import se.rit.edu.satd.SATDDifference;
import se.rit.edu.satd.SATDInstance;
import se.rit.edu.satd.detector.SATDDetector;
import se.rit.edu.util.ElapsedTimer;
import se.rit.edu.util.GroupedComment;
import se.rit.edu.util.NullGroupedComment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mines the differences in SATD between repositories
 */
public class RepositoryDiffMiner {

    // Required fields
    private RepositoryCommitReference firstRepo;
    private RepositoryCommitReference secondRepo = null;
    private SATDDetector satdDetector = null;

    // Timer for metrics reporting
    private ElapsedTimer diffTimer = null;
    private ElapsedTimer mineTimer = null;

    private RepositoryDiffMiner() {}

    /**
     * Generates an initial RepositoryDiffMiner Object
     * @param repo The earlier of the repository commit references
     * @return a RepositoryDiffMiner object
     */
    public static RepositoryDiffMiner ofFirstRepository(RepositoryCommitReference repo) {
        RepositoryDiffMiner miner = new RepositoryDiffMiner();
        miner.firstRepo = repo;
        return miner;
    }

    /**
     * Adds a second repository to the repository diff miner
     * @param repo The later of the repository commit references
     * @return a RepositoryDiffMiner object
     */
    public RepositoryDiffMiner andSecondRepository(RepositoryCommitReference repo) {
        this.secondRepo = repo;
        return this;
    }

    /**
     * Adds an SATDDetector to the diff miner
     * @param detector an SATDDetector
     * @return a RepositoryDiffMiner object
     */
    public RepositoryDiffMiner usingDetector(SATDDetector detector) {
        this.satdDetector = detector;
        return this;
    }

    /**
     * Mines the differences in SATD between the two repositories set during generation
     * of the DiffMiner object
     * @return a SATDDifference object representing the SATD as it was changed between the
     * earlier and the latter tags
     * @throws IllegalStateException thrown if the DiffMiner object has not been fully
     * configured before running
     */
    public SATDDifference mineDiff() throws IllegalStateException {
        if( this.secondRepo == null ) {
            throw new IllegalStateException(
                    "Second repo to diff not set, please call andSecondRepository() to set the second repo.");
        }
        if( this.satdDetector == null ) {
            throw new IllegalStateException(
                    "SATD Detector not set, please call usingDetector() to set the SATD Detector.");
        }

        // Get the SATD occurrences for each repo
        final Map<String, List<GroupedComment>> olderSATD = this.firstRepo.getFilesToSAIDOccurrences(this.satdDetector);
        final Map<String, List<GroupedComment>> newerSATD = this.secondRepo.getFilesToSAIDOccurrences(this.satdDetector);

        this.startSATDDiffTimer();

        // Get file mapping for renamed files
        // The idea here is that SATD that is mapped will not require
        // Additional, more costly, processing using Git tools
        final Map<String, String> fileMapping =
                RepositoryFileMapping.getFileMapping(olderSATD.keySet(), newerSATD.keySet());

        // Create base diff object
        final SATDDifference difference = new SATDDifference(
                this.firstRepo.getProjectName(),
                this.firstRepo.getProjectURI(),
                this.firstRepo.getTag(),
                this.secondRepo.getTag());

        // Iterate through all SATD occurrences and determine what to classify it as
        olderSATD.keySet().forEach(oldKey -> {
            final String newKey = fileMapping.get(oldKey);
            if( newKey.equals(RepositoryFileMapping.NOT_FOUND) ) {
                difference.addFileRemovedSATD(olderSATD.get(oldKey).stream()
                        .map(comment ->
                                new SATDInstance(oldKey, SATDInstance.FILE_DEV_NULL, comment, new NullGroupedComment()))
                        .collect(Collectors.toList()));
            } else {
                // See which strings in each file are present here
                final List<MappedSATDComment> mappedOlderSATD = olderSATD.get(oldKey).stream()
                        .map(MappedSATDComment::new)
                        .collect(Collectors.toList());
                final List<MappedSATDComment> mappedNewerSATD = newerSATD.get(newKey).stream()
                        .map(MappedSATDComment::new)
                        .collect(Collectors.toList());
                // See what SATD can be mapped between files
                mappedNewerSATD.forEach(newerSATDMapping ->
                        mappedOlderSATD.stream()
                                .filter(MappedSATDComment::isNotMapped)
                                .filter(newerSATDMapping::commentMatches)
                                .findFirst()
                                .ifPresent(s -> {
                                    s.setMapped(newerSATDMapping);
                                    newerSATDMapping.setMapped(s);
                                })
                );

                // SATD that appears identically in both new and old repos
                // Means that it is has not been touched, and no further resources and
                // no resources should be spent determining whether the SATD was addressed
                List<SATDInstance> untouchedSATD = mappedOlderSATD.stream()
                        .filter(MappedSATDComment::isMapped)
                        .map(comment ->
                                new SATDInstance(oldKey, newKey, comment.getComment(),
                                        comment.getMappedSATDComment().getComment()))
                        .collect(Collectors.toList());
                // SATD that was not in the new repo, but was in the old repo
                // We will need to determine whether the SATD was changed or removed
                // We also ignore the case where the SATD was removed in one file, and transferred
                // to another file
                List<SATDInstance> changedOrRemovedSATD = mappedOlderSATD.stream()
                        .filter(MappedSATDComment::isNotMapped)
                        .map(MappedSATDComment::getComment)
                        .map(comment -> new SATDInstance(oldKey, SATDInstance.FILE_UNKNOWN,
                                comment, new NullGroupedComment()))
                        .collect(Collectors.toList());
                // SATD that was not in the old repo, but was in the repo
                // We will need to determine whether the SATD was changed from another
                // SATD instance, or added to the project
                List<SATDInstance> changedOrAddedSATD = mappedNewerSATD.stream()
                        .filter(MappedSATDComment::isNotMapped)
                        .map(MappedSATDComment::getComment)
                        .map(comment -> new SATDInstance(SATDInstance.FILE_DEV_NULL, newKey,
                                new NullGroupedComment(), comment))
                        .collect(Collectors.toList());

                // Add these processed instances to the difference object
                difference.addUnaddressedSATD(untouchedSATD);
                difference.addAddressedOrChangedSATD(changedOrRemovedSATD);
                difference.addChangedOrAddedSATD(changedOrAddedSATD);
            }
        });

        this.endSATDDiffTimer();

        this.startSATDCommitMineTimer();
        // Get commits for File Removed SATD
        this.setCommitsInDiff(difference);
        this.endSATDCommitMineTimer();

        return difference;
    }

    /**
     * Sets the commits for each SATD instance in a SATDDifference
     * @param diff a SATDDifference object
     */
    private void setCommitsInDiff(SATDDifference diff) {
        diff.getFileRemovedSATD().forEach( satd ->
                getCommitsForSATD(satd, new FileRemovedOrRenamedCommitLocator(), true));

        diff.getAddressedOrChangedSATD().forEach( satd ->
                getCommitsForSATD(satd, new SATDRemovedChangedMovedCommitLocator(), true));

        diff.getUnaddressedSATD().forEach( satd ->
                getCommitsForSATD(satd, new SATDUnaddressedCommitLocator(), true));

        diff.getChangedOrAddedSATD().forEach( satd ->
                getCommitsForSATD(satd, new SATDAddedCommitLocator(), false));

        diff.alignRemovedAndAddedForOverlaps();
    }

    /**
     * For a specific SATD instance, find the commits pertinent to it
     * @param satd an SATD instance
     * @param locator a CommitLocator object that contains the logic of how
     *                to locate the pertinent commits for the SATD instance
     * @param useOldFilePath True if the SATD instance should use the file path
     *                       of the older repository when locating the commit added,
     *                       else False if it should use the newer. This should always
     *                       be true unless the SATD instance does not have a valid
     *                       original file reference.
     */
    private void getCommitsForSATD(SATDInstance satd, CommitLocator locator, boolean useOldFilePath) {
        locator.findContributingCommits(this.firstRepo.getGitInstance(), satd,
                useOldFilePath ? this.firstRepo.getCommit() : this.secondRepo.getCommit(),
                useOldFilePath ? satd.getOldFile() : satd.getNewFile());
        locator.findCommitAddressed(this.firstRepo.getGitInstance(), satd,
                this.firstRepo.getCommit(), this.secondRepo.getCommit());
    }

    /**
     * Starts the elapsed diffTimer for determining the time it took to diff the
     * repositories.
     */
    private void startSATDDiffTimer() {
        this.diffTimer = new ElapsedTimer();
        this.diffTimer.start();
    }

    /**
     * Ends the elapsed time for determining the time it took to diff the
     * repositories, and also outputs the execution time.
     */
    private void endSATDDiffTimer() {
        this.diffTimer.end();
        System.out.println(String.format("Finished diffing against previous version in %,dms", this.diffTimer.readMS()));
    }

    private void startSATDCommitMineTimer() {
        this.mineTimer = new ElapsedTimer();
        this.mineTimer.start();
    }

    private void endSATDCommitMineTimer() {
        this.mineTimer.end();
        System.out.println(String.format("Finished mining commit data and finalizing diffs in %,dms", this.mineTimer.readMS()));
    }

    /**
     * A models class to store meta-models on whether a commit has been mapped between a new and old SATD instance
     */
    private class MappedSATDComment {

        private GroupedComment comment;
        private boolean isMapped = false;
        private MappedSATDComment otherComment = null;

        private MappedSATDComment(GroupedComment oldComment) {
            this.comment = oldComment;
        }

        private GroupedComment getComment() {
            return this.comment;
        }

        private boolean commentMatches(MappedSATDComment other) {
            return this.comment.getComment().equals(other.getComment().getComment());
        }

        private boolean isMapped() {
            return this.isMapped;
        }

        private boolean isNotMapped() {
            return !this.isMapped;
        }

        private MappedSATDComment getMappedSATDComment() {
            return this.otherComment;
        }

        private void setMapped(MappedSATDComment other) {
            if( this.isMapped ) {
                System.err.println("It is likely that an SATD instance was unintentionally mapped twice. " +
                                "This will throw off output models.");
                return;
            }
            this.isMapped = true;
            this.otherComment = other;
        }
    }
}
