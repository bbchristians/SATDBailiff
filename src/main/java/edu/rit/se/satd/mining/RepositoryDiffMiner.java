package edu.rit.se.satd.mining;

import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.satd.comment.GroupedComment;
import edu.rit.se.satd.comment.OldToNewCommentMapping;
import edu.rit.se.satd.comment.RepositoryComments;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.mining.commit.CommitToCommitDiff;
import edu.rit.se.satd.model.SATDDifference;
import edu.rit.se.satd.model.SATDInstance;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.*;
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
        final CommitToCommitDiff cToCDiff = new CommitToCommitDiff(
                this.firstRepo, this.secondRepo, this.satdDetector);

        // Get the SATD occurrences for each repo
        final Map<String, RepositoryComments> olderSATD = this.firstRepo.getFilesToSATDOccurrences(
                this.satdDetector, cToCDiff.getModifiedFilesOld());
        final Map<String, RepositoryComments> newerSATD = this.secondRepo.getFilesToSATDOccurrences(
                this.satdDetector, cToCDiff.getModifiedFilesNew());

        final Map<String, RepositoryComments> impactedNewSATD = newerSATD.entrySet().stream()
                .map(entry -> {
                    final String fileName = entry.getKey();
                    final RepositoryComments comments = entry.getValue();
                    final List<GroupedComment> impactedComments = comments.getComments().stream()
                            .filter(comment -> cToCDiff.commentWasImpactedInNewRepo(fileName, comment))
                            .collect(Collectors.toList());
                    return new HashMap.SimpleEntry<>(fileName, comments.withNewComments(impactedComments));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if( impactedNewSATD.values().stream().anyMatch(asd -> asd.getComments().size() > 0 ) ) {
            System.err.println("HERE:");
        }


//        final List<String> erroredFiles = new ArrayList<>();
//        // Add errored files to known errors
//        erroredFiles.addAll(newerSATD.values().stream()
//                .map(RepositoryComments::getParseErrorFiles)
//                .flatMap(Collection::stream)
//                .collect(Collectors.toList()));
//        erroredFiles.addAll(olderSATD.values().stream()
//                .map(RepositoryComments::getParseErrorFiles)
//                .flatMap(Collection::stream)
//                .collect(Collectors.toList()));



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

    private static List<SATDInstance> mineDiffsFromMappedSATDInstances(CommitToCommitDiff cToCDiff,
                                                                       List<OldToNewCommentMapping> mappings,
                                                                       boolean isOld) {
        return mappings.stream()
                        .filter(OldToNewCommentMapping::isNotMapped)
                        .flatMap(mapping -> {
                                    final List<SATDInstance> minedInstances = (isOld ?
                                            cToCDiff.loadDiffsForOldFile(mapping.getFile(), mapping.getComment()) :
                                            cToCDiff.loadDiffsForNewFile(mapping.getFile(), mapping.getComment()));
                                    return minedInstances.stream()
                                            .peek(a -> a.setDuplicationId(mapping.getDuplicationId()));
                        })
                        .collect(Collectors.toList());
    }

    private static void populateDuplicationIds(List<OldToNewCommentMapping> mappingList) {
        final Map<OldToNewCommentMapping, Integer> curDupIds = new HashMap<>();
        mappingList.forEach(mapping -> {
            if( !curDupIds.containsKey(mapping) ) {
                curDupIds.put(mapping, 0);
            }
            mapping.setDuplicationId(curDupIds.get(mapping));
            curDupIds.put(mapping, curDupIds.get(mapping) + 1);
        });
    }

    public String getDiffString() {
        return this.secondRepo.getCommitHash();
    }
}
