package se.rit.edu.satd.mining;

import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.satd.SATDDifference;
import se.rit.edu.satd.detector.SATDDetector;
import se.rit.edu.util.ElapsedTimer;
import se.rit.edu.util.GroupedComment;

/**
 * Mines the differences in SATD between repositories
 */
public abstract class RepositoryDiffMiner {

    // Required fields
    RepositoryCommitReference firstRepo;
    RepositoryCommitReference secondRepo = null;
    SATDDetector satdDetector = null;

    // Timer for metrics reporting
    private ElapsedTimer diffTimer = null;
    private ElapsedTimer mineTimer = null;

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
        return new SATDDifference(
                this.firstRepo.getProjectName(),
                this.firstRepo.getProjectURI(),
                this.firstRepo.getCommit(),
                this.secondRepo.getCommit());
    }

    /**
     * Starts the elapsed diffTimer for determining the time it took to diff the
     * repositories.
     */
    void startSATDDiffTimer() {
        this.diffTimer = new ElapsedTimer();
        this.diffTimer.start();
    }

    /**
     * Ends the elapsed time for determining the time it took to diff the
     * repositories, and also outputs the execution time.
     */
    void endSATDDiffTimer() {
        this.diffTimer.end();
        System.out.println(String.format("Finished diffing against previous version in %,dms", this.diffTimer.readMS()));
    }

    void startSATDCommitMineTimer() {
        this.mineTimer = new ElapsedTimer();
        this.mineTimer.start();
    }

    void endSATDCommitMineTimer() {
        this.mineTimer.end();
        System.out.println(String.format("Finished mining commit data and finalizing diffs in %,dms", this.mineTimer.readMS()));
    }

    /**
     * A models class to store meta-models on whether a commit has been mapped between a new and old SATD instance
     */
    class MappedSATDComment {

        private GroupedComment comment;
        private boolean isMapped = false;
        private MappedSATDComment otherComment = null;

        MappedSATDComment(GroupedComment oldComment) {
            this.comment = oldComment;
        }

        GroupedComment getComment() {
            return this.comment;
        }

        boolean commentMatches(MappedSATDComment other) {
            return this.comment.getComment().equals(other.getComment().getComment());
        }

        boolean isMapped() {
            return this.isMapped;
        }

        boolean isNotMapped() {
            return !this.isMapped;
        }

        MappedSATDComment getMappedSATDComment() {
            return this.otherComment;
        }

        void setMapped(MappedSATDComment other) {
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
