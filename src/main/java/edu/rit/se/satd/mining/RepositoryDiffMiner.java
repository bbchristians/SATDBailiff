package edu.rit.se.satd.mining;

import edu.rit.se.satd.SATDDifference;
import edu.rit.se.satd.SATDInstance;
import edu.rit.se.satd.comment.NullGroupedComment;
import edu.rit.se.util.MappingPair;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.satd.comment.GroupedComment;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.commit.CommitToCommitDiff;
import lombok.AllArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mines the differences in SATD between repositories
 */
@AllArgsConstructor
public class RepositoryDiffMiner {

    // Required fields
    private RepositoryCommitReference firstRepo;
    private RepositoryCommitReference secondRepo;
    private SATDDetector satdDetector;

    /**
     * Mines the differences in SATD between the two repositories set during generation
     * of the DiffMiner object
     * @return a SATDDifference object representing the SATD as it was changed between the
     * earlier and the latter tags
     * @throws IllegalStateException thrown if the DiffMiner object has not been fully
     * configured before running
     */
    public SATDDifference mineDiff() {
        final SATDDifference diff = new SATDDifference(
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

        // Add SATD Instances that were in the OLD repo, but possibly modified in the new repo
        diff.addSATDInstances(
                olderSATD.keySet().stream()
                        .flatMap(fileInOldRepo -> olderSATD.get(fileInOldRepo).stream()
                                .filter(comment ->
                                        !newerSATD.keySet().contains(fileInOldRepo) ||
                                                !newerSATD.get(fileInOldRepo).contains(comment))
                                .map(comment -> new MappingPair(fileInOldRepo, comment)))
                        .map(pair -> cToCDiff.loadDiffsFor(pair.getFirst().toString(), (GroupedComment) pair.getSecond()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())
        );
        // Add SATD instances that were in the NEW repo, but not in the old repo
//        diff.addSATDInstances(
//                newerSATD.keySet().stream()
//                        .flatMap(fileInNewRepo -> newerSATD.get(fileInNewRepo).stream()
//                                .filter(comment ->
//                                        !olderSATD.keySet().contains(fileInNewRepo) ||
//                                                !olderSATD.get(fileInNewRepo).contains(comment))
//                                .map(comment -> new MappingPair(fileInNewRepo, comment)))
//                        .map(pair -> new SATDInstance(
//                                "/dev/null",
//                                pair.getFirst().toString(),
//                                new NullGroupedComment(),
//                                (GroupedComment) pair.getSecond(),
//                                SATDInstance.SATDResolution.SATD_ADDED))
//                        .collect(Collectors.toList())
//        );

        return diff;
    }
}
