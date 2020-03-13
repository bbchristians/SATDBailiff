package edu.rit.se.satd.mining;

import edu.rit.se.satd.SATDDifference;
import edu.rit.se.satd.comment.OldToNewCommentMapping;
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

        final List<OldToNewCommentMapping> satdInOldNotInNew = olderSATD.keySet().stream()
                .flatMap(oldFile -> olderSATD.get(oldFile).stream()
                    .map(comment -> new OldToNewCommentMapping(comment, oldFile)))
                .collect(Collectors.toList());
        final List<OldToNewCommentMapping> satdInNewNotInOld = newerSATD.keySet().stream()
                .flatMap(newFile -> newerSATD.get(newFile).stream()
                        .map(comment -> new OldToNewCommentMapping(comment, newFile)))
                .collect(Collectors.toList());
        alignMappingLists(satdInNewNotInOld, satdInOldNotInNew);
        alignMappingLists(satdInOldNotInNew, satdInNewNotInOld);


        // Add SATD Instances that were in the OLD repo, but possibly modified in the new repo
        diff.addSATDInstances(
                satdInOldNotInNew.stream()
                        .filter(OldToNewCommentMapping::isNotMapped)
                        .map(mapping -> cToCDiff.loadDiffsForFile(mapping.getFile(), mapping.getComment(), true))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())
        );
        // Add SATD instances that were in the NEW repo, but not in the old repo
        diff.addSATDInstances(
                satdInNewNotInOld.stream()
                        .filter(OldToNewCommentMapping::isNotMapped)
                        .map(mapping -> cToCDiff.loadDiffsForFile(mapping.getFile(), mapping.getComment(), false))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())
        );

        return diff;
    }

    private static void alignMappingLists(List<OldToNewCommentMapping> list1, List<OldToNewCommentMapping> list2) {
        list1.forEach(mappedComment ->
                list2.stream()
                        .filter(OldToNewCommentMapping::isNotMapped)
                        .filter(mappedComment::commentsMatch)
                        .findFirst()
                        .ifPresent(mappedComment::mapTo)
        );
    }
}
