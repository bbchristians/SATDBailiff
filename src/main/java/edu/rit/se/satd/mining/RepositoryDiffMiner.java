package edu.rit.se.satd.mining;

import edu.rit.se.satd.SATDDifference;
import edu.rit.se.util.MappingPair;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.satd.comment.GroupedComment;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.commit.CommitToCommitDiff;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mines the differences in SATD between repositories
 */
public class RepositoryDiffMiner {

    // Required fields
    RepositoryCommitReference firstRepo;
    RepositoryCommitReference secondRepo = null;
    SATDDetector satdDetector = null;

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
        SATDDifference diff = new SATDDifference(
                this.firstRepo.getProjectName(),
                this.firstRepo.getProjectURI(),
                this.firstRepo.getCommit(),
                this.secondRepo.getCommit());

        // Load the diffs between versions
        final CommitToCommitDiff cToCDiff = new CommitToCommitDiff(this.firstRepo, this.secondRepo);

        // Get the SATD occurrences for each repo
        final Map<String, List<GroupedComment>> newerSATD = this.secondRepo.getFilesToSATDOccurrences(
                this.satdDetector, cToCDiff.getModifiedFilesNew());
        final Map<String, List<GroupedComment>> olderSATD = this.firstRepo.getFilesToSATDOccurrences(
                this.satdDetector, cToCDiff.getModifiedFilesOld());

        diff.addSATDInstances(
                olderSATD.keySet().stream()
                        .flatMap(fileInOldRepo -> olderSATD.get(fileInOldRepo).stream()
                                .filter(comment ->
                                        !newerSATD.keySet().contains(fileInOldRepo) ||
                                                !newerSATD.get(fileInOldRepo).contains(comment))
                                .map(comment -> new MappingPair(fileInOldRepo, comment)))
                        .map(pair -> cToCDiff.loadDiffsFor(diff, pair.getFirst().toString(), (GroupedComment) pair.getSecond()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())
        );

        return diff;
    }

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
}
