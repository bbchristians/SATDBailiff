package edu.rit.se.satd.mining;

import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.satd.SATDDifference;
import edu.rit.se.satd.comment.GroupedComment;
import edu.rit.se.satd.comment.OldToNewCommentMapping;
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

        // Get a list of all SATD instances as a mappable instance
        final List<OldToNewCommentMapping> oldSATDMappings = olderSATD.keySet().stream()
                .flatMap(oldFile -> olderSATD.get(oldFile).stream()
                    .map(comment -> new OldToNewCommentMapping(comment, oldFile)))
                .collect(Collectors.toList());
        final List<OldToNewCommentMapping> newSATDMappings = newerSATD.keySet().stream()
                .flatMap(newFile -> newerSATD.get(newFile).stream()
                        .map(comment -> new OldToNewCommentMapping(comment, newFile)))
                .collect(Collectors.toList());

        // Map the new to old and then old to new, so we can determine which SATD instances
        // may have changed
        alignMappingLists(newSATDMappings, oldSATDMappings);
        alignMappingLists(oldSATDMappings, newSATDMappings);

        // Add SATD Instances that were in the OLD repo, but couldn't be mapped to the NEW repo
        mineDiffsFromMappedSATDInstances(diff, cToCDiff, oldSATDMappings, true);
        // Add SATD instances that were in the NEW repo, but couldn't be mapped to the OLD repo
        mineDiffsFromMappedSATDInstances(diff, cToCDiff, newSATDMappings, false);

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

    private static void mineDiffsFromMappedSATDInstances(SATDDifference diffInstance, CommitToCommitDiff cToCDiff,
                                                         List<OldToNewCommentMapping> mappings, boolean isOld) {
        diffInstance.addSATDInstances(
                mappings.stream()
                        .filter(OldToNewCommentMapping::isNotMapped)
                        .peek(mapping -> System.out.println("Found unmapped comment (isOld="+isOld+"): \n" + mapping.getComment().getComment()))
                        .map(mapping -> isOld ?
                                cToCDiff.loadDiffsForOldFile(mapping.getFile(), mapping.getComment()) :
                                cToCDiff.loadDiffsForNewFIle(mapping.getFile(), mapping.getComment()))
                        .peek(instances ->
                                System.out.println("Found " + instances.size() + " diffs!"))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())
        );
    }
}
