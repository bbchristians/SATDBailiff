package edu.rit.se.satd.mining;

import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.satd.comment.OldToNewCommentMapping;
import edu.rit.se.satd.comment.RepositoryComments;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.commit.CommitToCommitDiff;
import edu.rit.se.satd.model.SATDDifference;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mines the differences in SATD between repositories
 */
@RequiredArgsConstructor
public class RepositoryDiffMiner {

    // Required fields
    @NonNull
    private RepositoryCommitReference firstRepo;
    @NonNull
    @Getter
    private RepositoryCommitReference secondRepo;
    @NonNull
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
                this.secondRepo.getProjectName(),
                this.secondRepo.getProjectURI(),
                this.firstRepo.getCommit(),
                this.secondRepo.getCommit());

        // Load the diffs between versions
        final CommitToCommitDiff cToCDiff = new CommitToCommitDiff(this.firstRepo, this.secondRepo);

        // Get the SATD occurrences for each repo
        final Map<String, RepositoryComments> newerSATD = this.secondRepo.getFilesToSATDOccurrences(
                this.satdDetector, cToCDiff.getModifiedFilesNew());
        final Map<String, RepositoryComments> olderSATD = this.firstRepo.getFilesToSATDOccurrences(
                this.satdDetector, cToCDiff.getModifiedFilesOld());

        // Get a list of all SATD instances as a mappable instance
        final List<OldToNewCommentMapping> oldSATDMappings = olderSATD.keySet().stream()
                .flatMap(oldFile -> olderSATD.get(oldFile).getComments().stream()
                    .map(comment -> new OldToNewCommentMapping(comment, oldFile)))
                .collect(Collectors.toList());
        final List<OldToNewCommentMapping> newSATDMappings = newerSATD.keySet().stream()
                .flatMap(newFile -> newerSATD.get(newFile).getComments().stream()
                        .map(comment -> new OldToNewCommentMapping(comment, newFile)))
                .collect(Collectors.toList());
        final List<String> erroredFiles = new ArrayList<>();
        // Add errored files to known errors
        erroredFiles.addAll(newerSATD.values().stream()
                .map(RepositoryComments::getParseErrorFiles)
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));
        erroredFiles.addAll(olderSATD.values().stream()
                .map(RepositoryComments::getParseErrorFiles)
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));

        // Map the new to old and then old to new, so we can determine which SATD instances
        // may have changed
        alignMappingLists(oldSATDMappings, newSATDMappings, erroredFiles);

        // Add SATD Instances that were in the OLD repo, but couldn't be mapped to the NEW repo
        mineDiffsFromMappedSATDInstances(diff, cToCDiff, oldSATDMappings, true);
        // Add SATD instances that were in the NEW repo, but couldn't be mapped to the OLD repo
        mineDiffsFromMappedSATDInstances(diff, cToCDiff, newSATDMappings, false);

        return diff;
    }

    private static void alignMappingLists(List<OldToNewCommentMapping> list1, List<OldToNewCommentMapping> list2,
                                          List<String> erroredFiles) {
        list1.forEach(mappedComment ->
                list2.stream()
                        .filter(OldToNewCommentMapping::isNotMapped)
                        .filter(mappedComment::commentsMatch)
                        .findFirst()
                        .ifPresent(mappedComment::mapTo)
        );
        list1.stream()
                .filter(c -> erroredFiles.contains(c.getFile()))
                .forEach(c -> c.mapTo(null));
        list2.stream()
                .filter(c -> erroredFiles.contains(c.getFile()))
                .forEach(c -> c.mapTo(null));
    }

    private static void mineDiffsFromMappedSATDInstances(SATDDifference diffInstance, CommitToCommitDiff cToCDiff,
                                                         List<OldToNewCommentMapping> mappings, boolean isOld) {
        diffInstance.addSATDInstances(
                mappings.stream()
                        .filter(OldToNewCommentMapping::isNotMapped)
                        .map(mapping -> isOld ?
                                cToCDiff.loadDiffsForOldFile(mapping.getFile(), mapping.getComment()) :
                                cToCDiff.loadDiffsForNewFile(mapping.getFile(), mapping.getComment()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList())
        );
    }

    public String getDiffString() {
        return String.format("%s#diff-%s", this.firstRepo.getCommitHash(), this.secondRepo.getCommitHash());
    }
}
