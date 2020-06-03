package edu.rit.se.satd.mining.commit;

import com.github.gumtreediff.actions.model.Action;
import edu.rit.se.git.GitUtil;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.satd.comment.GroupedComment;
import edu.rit.se.satd.comment.NullGroupedComment;
import edu.rit.se.satd.comment.RepositoryComments;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.model.SATDInstanceInFile;
import edu.rit.se.util.GumTreeUtil;
import edu.rit.se.util.JavaParseUtil;
import edu.rit.se.util.KnownParserException;
import edu.rit.se.util.SimilarityUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


public class CommitToCommitDiff {

    private final Git gitInstance;
    private final RevCommit newCommit;
    private final Map<FileToFileMapping, List<Action>> actionsTaken;
    private final SATDDetector detector;

    public static DiffAlgorithm diffAlgo = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS);

    public CommitToCommitDiff(RepositoryCommitReference oldRepo,
                              RepositoryCommitReference newRepo, SATDDetector detector) {
        this.gitInstance = newRepo.getGitInstance();
        this.newCommit = newRepo.getCommit();
        this.actionsTaken = GumTreeUtil.getEditScript(this.gitInstance, oldRepo.getCommit(), newRepo.getCommit());
        this.detector = detector;
    }

    public List<String> getModifiedFilesNew() {
        return this.actionsTaken.keySet().stream()
                .map(FileToFileMapping::getF2)
                .collect(Collectors.toList());
    }

    public List<String> getModifiedFilesOld() {
        return this.actionsTaken.keySet().stream()
                .map(FileToFileMapping::getF1)
                .collect(Collectors.toList());
    }

    public boolean commentWasImpactedInOldRepo(String fileName, GroupedComment comment) {
        return commentWasImpacted(fileName, comment, true);
    }

    public boolean commentWasImpactedInNewRepo(String fileName, GroupedComment comment) {
        return commentWasImpacted(fileName, comment, false);
    }

    private boolean commentWasImpacted(String fileName, GroupedComment comment, boolean useF1) {
        Optional<FileToFileMapping> thisMapping = this.actionsTaken.keySet().stream()
                .filter(fMap -> useF1 ? fMap.getF1().equals(fileName) : fMap.getF2().equals(fileName))
                .findFirst();
        if( thisMapping.isPresent() ) {
            String fileContents = useF1 ? thisMapping.get().getF1Contents() : thisMapping.get().getF2Contents();
            int satdStartPos = GumTreeUtil.getGumtreePosFromJavaParserPos(fileContents, comment.getStart());
            int satdEndPos = GumTreeUtil.getGumtreePosFromJavaParserPos(fileContents, comment.getEnd());
            return this.actionsTaken.get(thisMapping.get()).stream()
                    .filter(a -> intRangesOverlap(a.getNode().getPos(), a.getNode().getLength(), satdStartPos, satdEndPos))
                    .findFirst().isPresent();
        }
        System.err.println("This should not happen");
        return false;
    }

    public List<SATDInstance> loadDiffsForOldFile(String oldFile, GroupedComment comment) {
//        return this.diffEntries.stream()
//                .filter(entry -> entry.getOldPath().equals(oldFile))
//                .map(diffEntry -> this.getSATDFromDiffOldFile(diffEntry, comment))
//                .flatMap(Collection::stream)
//                .collect(Collectors.toList());
        return new ArrayList<>();

    }

    public List<SATDInstance> loadDiffsForNewFile(String newFile, GroupedComment comment) {
//        return this.diffEntries.stream()
//                .filter(entry -> entry.getNewPath().equals(newFile))
//                .map(diffEntry -> this.getSATDDiffFromNewFile(diffEntry, comment))
//                .flatMap(Collection::stream)
//                .collect(Collectors.toList());
        return new ArrayList<>();
    }

    private List<SATDInstance> getSATDFromDiffOldFile(DiffEntry diffEntry, GroupedComment oldComment) {
        final List<SATDInstance> satd = new ArrayList<>();

//        switch (diffEntry.getChangeType()) {
//            case RENAME:
//                final RepositoryComments comInNewRepository =
//                        this.getCommentsInFileInNewRepository(diffEntry.getNewPath());
//                final GroupedComment newComment = comInNewRepository.getComments().stream()
//                        .filter(nc -> nc.getComment().equals(oldComment.getComment()))
//                        .filter(nc -> nc.getContainingMethod().equals(oldComment.getContainingMethod()))
//                        .findFirst()
//                        .orElse(new NullGroupedComment());
//                // If the SATD couldn't be found in the new file, then it must have been removed
//                final SATDInstance.SATDResolution resolution = ( newComment instanceof NullGroupedComment ) ?
//                        SATDInstance.SATDResolution.SATD_REMOVED : SATDInstance.SATDResolution.FILE_PATH_CHANGED;
//                satd.add(
//                        new SATDInstance(
//                                new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
//                                new SATDInstanceInFile(diffEntry.getNewPath(), newComment),
//                                resolution
//                ));
//                break;
//            case DELETE:
//                satd.add(
//                        new SATDInstance(
//                                new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
//                                new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
//                                SATDInstance.SATDResolution.FILE_REMOVED
//                        )
//                );
//                break;
//            case MODIFY:
//                // get the edits to the file, and the deletions to the SATD we're concerned about
//                final List<Edit> editsToFile = this.getEdits(diffEntry);
//                final List<Edit> editsToSATDComment = editsToFile.stream()
//                        .filter(edit -> editImpactedComment(edit, oldComment, 0, true))
//                        .collect(Collectors.toList());
//                // Find the comments in the new repository version
//                final RepositoryComments commentsInNewRepository =
//                        this.getCommentsInFileInNewRepository(diffEntry.getNewPath());
//                // Find the comments created by deleting
//                final List<GroupedComment> updatedComments = editsToSATDComment.stream()
//                        .flatMap( edit -> commentsInNewRepository.getComments().stream()
//                                .filter( c -> editImpactedComment(edit, c, Math.max(0, oldComment.numLines() - c.numLines()), false)))
//                        .collect(Collectors.toList());
//                // If changes were made to the SATD comment, and now the comment is missing
//                if( updatedComments.isEmpty() && !editsToSATDComment.isEmpty()) {
//                    satd.add(
//                            new SATDInstance(
//                                    new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
//                                    new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
//                                    SATDInstance.SATDResolution.SATD_REMOVED
//                            )
//                    );
//                }
//                // If an updated comment was found, and it is not identical to the old comment
//                if( !updatedComments.isEmpty() &&
//                        updatedComments.stream()
//                                .map(GroupedComment::getComment)
//                                .noneMatch(oldComment.getComment()::equals)) {
//                    satd.addAll(
//                            updatedComments.stream()
//                                    .map(nc -> {
//                                        // If the comment that was added is similar enough to the old comment
//                                        // we can infer that the comment was changed
//                                        if( SimilarityUtil.commentsAreSimilar(oldComment, nc) ) {
//                                            // If the new comment is still SATD, then the instance is changed
//                                            if( this.detector.isSATD(nc.getComment()) ) {
//                                                return new SATDInstance(
//                                                        new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
//                                                        new SATDInstanceInFile(diffEntry.getNewPath(), nc),
//                                                        SATDInstance.SATDResolution.SATD_CHANGED);
//                                            }
//                                            // Otherwise the part of the comment that was making the comment SATD
//                                            // was removed, and so it can be determined that the SATD instance
//                                            // was removed
//                                            else {
//                                                return new SATDInstance(
//                                                        new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
//                                                        new SATDInstanceInFile(diffEntry.getNewPath(), nc),
//                                                        SATDInstance.SATDResolution.SATD_REMOVED);
//                                            }
//
//                                        } else {
//                                            // We know the comment was removed, and the one that was added
//                                            // was a different comment
//                                            return new SATDInstance(
//                                                    new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
//                                                    new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
//                                                    SATDInstance.SATDResolution.SATD_REMOVED);
//                                        }
//                                    })
//                                    .collect(Collectors.toList())
//                    );
//                }
//                if(oldComment.getContainingMethod().equals(NULL_FIELD) ||
//                        oldComment.getContainingClass().equals(NULL_FIELD) ||
//                        editsTouchedClassOrMethodSignatureOldComment(editsToFile, oldComment)) {
//                    // Check to see if the name of the containing method/class were updated
//                    commentsInNewRepository.getComments().stream()
//                            .filter(c -> c.getComment().equals(oldComment.getComment()))
//                            .filter(c -> !c.getContainingClass().equals(oldComment.getContainingClass()) ||
//                                    !c.getContainingMethod().equals(oldComment.getContainingMethod()))
//                            // Determine if the comment's method or class was renamed
//                            .filter(c -> editsToFile.stream().anyMatch( edit ->
//                                    editImpactedContainingClass(edit, c, false) ||
//                                            editImpactedContainingMethod(edit, c, false)))
//                            .map(nc -> new SATDInstance(
//                                    new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
//                                    new SATDInstanceInFile(diffEntry.getNewPath(), nc),
//                                    SATDInstance.SATDResolution.CLASS_OR_METHOD_CHANGED))
//                            .findFirst()
//                            .ifPresent(satd::add);
//                    return satd;
//                }
//                break;
//        }
        return satd;
    }

    private List<SATDInstance> getSATDDiffFromNewFile(DiffEntry diffEntry, GroupedComment newComment) {
        final List<SATDInstance> satd = new ArrayList<>();

//        switch (diffEntry.getChangeType()) {
//            case ADD:
//                satd.add(
//                        new SATDInstance(
//                                new SATDInstanceInFile(DEV_NULL, new NullGroupedComment()),
//                                new SATDInstanceInFile(diffEntry.getNewPath(), newComment),
//                                SATDInstance.SATDResolution.SATD_ADDED
//                        )
//                );
//                break;
//            case MODIFY: case RENAME: case COPY:
//                // Determine if the edit to the file touched the SATD
//                this.getEdits(diffEntry).stream()
//                        .filter(edit -> GitUtil.editOccursInNewFileBetween(
//                                edit, newComment.getStartLine(), newComment.getEndLine()))
//                        .findAny()
//                        .ifPresent(edit ->
//                                satd.add(
//                                        new SATDInstance(
//                                                new SATDInstanceInFile(DEV_NULL, new NullGroupedComment()),
//                                                new SATDInstanceInFile(diffEntry.getNewPath(), newComment),
//                                                SATDInstance.SATDResolution.SATD_ADDED
//                                        ))
//                        );
//                break;
//        }

        return satd;
    }

    private List<Edit> getEdits(DiffEntry entry) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(this.gitInstance.getRepository());
        formatter.setContext(0);
        formatter.setDiffAlgorithm(CommitToCommitDiff.diffAlgo);
        try {
            return formatter.toFileHeader(entry).toEditList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RepositoryComments getCommentsInFileInNewRepository(String fileName) {
        final RepositoryComments comments = new RepositoryComments();
        try {
            comments.addComments(JavaParseUtil.parseFileForComments(this.getFileContents(fileName), fileName));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (KnownParserException e) {
            comments.addParseErrorFile(e.getFileName());
        }
        return comments;
    }

    private InputStream getFileContents(String fileName) throws IOException {
        return this.gitInstance.getRepository().open(
                TreeWalk.forPath(this.gitInstance.getRepository(), fileName, this.newCommit.getTree()).getObjectId(0)
        ).openStream();
    }

//    private boolean editImpactedComment(Edit edit, GroupedComment comment, int boundIncrease, boolean isOld) {
//        return isOld ? GitUtil.editOccursInOldFileBetween(edit,
//                comment.getStartLine() - boundIncrease, comment.getEndLine() + boundIncrease)
//                : GitUtil.editOccursInNewFileBetween(edit,
//                comment.getStartLine() - boundIncrease, comment.getEndLine() + boundIncrease);
//    }

//    private boolean editImpactedContainingMethod(Edit edit, GroupedComment comment, boolean isOld) {
//        return isOld ?
//                GitUtil.editOccursInOldFileBetween(edit,
//                    comment.getContainingMethodDeclarationStart(),
//                    comment.getContainingMethodDeclarationEnd())
//                : GitUtil.editOccursInNewFileBetween(edit,
//                    comment.getContainingMethodDeclarationStart(),
//                    comment.getContainingMethodDeclarationEnd());
//    }
//
//    private boolean editImpactedContainingClass(Edit edit, GroupedComment comment, boolean isOld) {
//        return isOld ?
//                GitUtil.editOccursInOldFileBetween(edit,
//                    comment.getContainingClassDeclarationStart(),
//                    comment.getContainingClassDeclarationEnd())
//                : GitUtil.editOccursInNewFileBetween(edit,
//                    comment.getContainingClassDeclarationStart(),
//                    comment.getContainingClassDeclarationEnd());
//    }

//    private boolean editsTouchedClassOrMethodSignatureOldComment(List<Edit> edits, GroupedComment oldComment) {
//        return edits.stream().anyMatch( edit ->
//                editImpactedContainingClass(edit, oldComment, true) ||
//                        editImpactedContainingMethod(edit, oldComment, true));
//    }

    private static boolean intRangesOverlap(int x1, int x2, int y1, int y2) {
        return Math.max(x1, y1) <= Math.min(x2, y2);
    }

}
