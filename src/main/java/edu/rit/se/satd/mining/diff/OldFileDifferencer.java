package edu.rit.se.satd.mining.diff;

import edu.rit.se.git.GitUtil;
import edu.rit.se.satd.comment.model.GroupedComment;
import edu.rit.se.satd.comment.model.NullGroupedComment;
import edu.rit.se.satd.comment.model.RepositoryComments;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.model.SATDInstanceInFile;
import edu.rit.se.util.JavaParseUtil;
import edu.rit.se.util.KnownParserException;
import javafx.util.Pair;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static edu.rit.se.satd.comment.model.NullGroupedComment.NULL_FIELD;

public class OldFileDifferencer extends FileDifferencer {

    private final RevCommit newCommit;

    private final SATDDetector detector;

    private final List<DiffEntry> otherDiffEntries;

    OldFileDifferencer(Git gitInstance, RevCommit newCommit, SATDDetector detector, List<DiffEntry> otherDiffEntries) {
        super(gitInstance);
        this.newCommit = newCommit;
        this.detector = detector;
        // Remove all entries that detail removed files --
        //   We won't need to look through these for changed comments
        this.otherDiffEntries = otherDiffEntries.stream()
                .filter(de -> !de.getNewPath().equals("/dev/null"))
                .collect(Collectors.toList());
    }

    @Override
    public String getPertinentFilePath(DiffEntry entry) {
        return entry.getOldPath();
    }

    @Override
    public List<SATDInstance> getInstancesFromFile(DiffEntry diffEntry, GroupedComment oldComment) {
        final List<SATDInstance> satd = new ArrayList<>();

        switch (diffEntry.getChangeType()) {
            case RENAME:
                final RepositoryComments comInNewRepository =
                        this.getCommentsInFileInNewRepository(diffEntry.getNewPath());
                final GroupedComment newComment = comInNewRepository.getComments().stream()
                        .filter(nc -> nc.getComment().equals(oldComment.getComment()))
                        .filter(nc -> nc.getContainingMethod().equals(oldComment.getContainingMethod()))
                        .findFirst()
                        .orElse(new NullGroupedComment());
                // If the SATD couldn't be found in the new file, then it must have been removed
                final SATDInstance.SATDResolution resolution = ( newComment instanceof NullGroupedComment ) ?
                        SATDInstance.SATDResolution.SATD_REMOVED : SATDInstance.SATDResolution.FILE_PATH_CHANGED;
                satd.add(
                        new SATDInstance(
                                new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                new SATDInstanceInFile(diffEntry.getNewPath(), newComment),
                                resolution
                        ));
                break;
            case DELETE:
                satd.add(
                        new SATDInstance(
                                new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
                                SATDInstance.SATDResolution.FILE_REMOVED
                        )
                );
                break;
            case MODIFY:
                // get the edits to the file, and the deletions to the SATD we're concerned about
                final List<Edit> editsToFile = this.getEdits(diffEntry);
                final List<Edit> editsToSATDComment = editsToFile.stream()
                        .filter(edit -> editImpactedComment(edit, oldComment, 0, true))
                        .collect(Collectors.toList());
                // Find the comments in the new repository version
                final RepositoryComments commentsInNewRepository =
                        this.getCommentsInFileInNewRepository(diffEntry.getNewPath());
                // Find the comments created by deleting
                final List<GroupedComment> updatedComments = editsToSATDComment.stream()
                        .flatMap( edit -> commentsInNewRepository.getComments().stream()
                                .filter( c -> editImpactedComment(edit, c,
                                        Math.max(0, oldComment.numLines() - c.numLines()), false)))
                        .collect(Collectors.toList());
                // If changes were made to the SATD comment, and now the comment is missing
                if( updatedComments.isEmpty() && !editsToSATDComment.isEmpty()) {
                    // Check to see if the SATD was moved to any other files
                    final List<SATDInstanceInFile> identicalCommentsInOtherFiles =
                            this.getOtherInstancesInCommitFiles(oldComment, diffEntry.getOldPath());
                    // No identical instances found, so we can assume the SATD was removed
                    if(identicalCommentsInOtherFiles.isEmpty()) {
                        satd.add(
                                new SATDInstance(
                                        new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                        new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
                                        SATDInstance.SATDResolution.SATD_REMOVED
                                )
                        );
                    }
                    // We found an instance in another modified file, so it may have been moved here.
                    else {
                        identicalCommentsInOtherFiles.stream()
                                .map(newInstance -> new SATDInstance(
                                        new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                        newInstance,
                                        SATDInstance.SATDResolution.SATD_MOVED_FILE
                                )).forEach(satd::add);
                    }
                }
                // If an updated comment was found, and it is not identical to the old comment
                if( !updatedComments.isEmpty() &&
                        updatedComments.stream()
                                .map(GroupedComment::getComment)
                                .noneMatch(oldComment.getComment()::equals)) {
                    satd.addAll(
                            updatedComments.stream()
                                    .map(nc -> {
                                        // If the new comment is still SATD, then the instance is changed
                                        if( this.detector.isSATD(nc.getComment()) ) {
                                            return new SATDInstance(
                                                    new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                                    new SATDInstanceInFile(diffEntry.getNewPath(), nc),
                                                    SATDInstance.SATDResolution.SATD_CHANGED);
                                        }
                                        // Otherwise the part of the comment that was making the comment SATD
                                        // was removed, and so it can be determined that the SATD instance
                                        // was removed
                                        else {
                                            return new SATDInstance(
                                                    new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                                    new SATDInstanceInFile(diffEntry.getNewPath(), nc),
                                                    SATDInstance.SATDResolution.SATD_REMOVED);
                                        }
                                    })
                                    .collect(Collectors.toList())
                    );
                }
                if(oldComment.getContainingMethod().equals(NULL_FIELD) ||
                        oldComment.getContainingClass().equals(NULL_FIELD) ||
                        editsTouchedClassOrMethodSignatureOldComment(editsToFile, oldComment)) {
                    // Check to see if the name of the containing method/class were updated
                    commentsInNewRepository.getComments().stream()
                            .filter(c -> c.getComment().equals(oldComment.getComment()))
                            .filter(c -> !c.getContainingClass().equals(oldComment.getContainingClass()) ||
                                    !c.getContainingMethod().equals(oldComment.getContainingMethod()))
                            // Determine if the comment's method or class was renamed
                            .filter(c -> editsToFile.stream().anyMatch( edit ->
                                    editImpactedContainingClass(edit, c, false) ||
                                            editImpactedContainingMethod(edit, c, false)))
                            .map(nc -> new SATDInstance(
                                    new SATDInstanceInFile(diffEntry.getOldPath(), oldComment),
                                    new SATDInstanceInFile(diffEntry.getNewPath(), nc),
                                    SATDInstance.SATDResolution.CLASS_OR_METHOD_CHANGED))
                            .findFirst()
                            .ifPresent(satd::add);
                    return satd;
                }
                break;
        }
        return satd;
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
        final TreeWalk walker = TreeWalk.forPath(this.gitInstance.getRepository(), fileName, this.newCommit.getTree());
        return this.gitInstance.getRepository().open(walker.getObjectId(0)).openStream();
    }

    private boolean editImpactedComment(Edit edit, GroupedComment comment, int boundIncrease, boolean isOld) {
        return isOld ? GitUtil.editOccursInOldFileBetween(edit,
                comment.getStartLine() - boundIncrease, comment.getEndLine() + boundIncrease)
                : GitUtil.editOccursInNewFileBetween(edit,
                comment.getStartLine() - boundIncrease, comment.getEndLine() + boundIncrease);
    }

    private boolean editsTouchedClassOrMethodSignatureOldComment(List<Edit> edits, GroupedComment oldComment) {
        return edits.stream().anyMatch( edit ->
                editImpactedContainingClass(edit, oldComment, true) ||
                        editImpactedContainingMethod(edit, oldComment, true));
    }


    private boolean editImpactedContainingMethod(Edit edit, GroupedComment comment, boolean isOld) {
        return isOld ?
                GitUtil.editOccursInOldFileBetween(edit,
                        comment.getContainingMethodDeclarationLineStart(),
                        comment.getContainingMethodDeclarationLineEnd())
                : GitUtil.editOccursInNewFileBetween(edit,
                comment.getContainingMethodDeclarationLineStart(),
                comment.getContainingMethodDeclarationLineEnd());
    }

    private boolean editImpactedContainingClass(Edit edit, GroupedComment comment, boolean isOld) {
        return isOld ?
                GitUtil.editOccursInOldFileBetween(edit,
                        comment.getContainingClassDeclarationLineStart(),
                        comment.getContainingClassDeclarationLineEnd())
                : GitUtil.editOccursInNewFileBetween(edit,
                comment.getContainingClassDeclarationLineStart(),
                comment.getContainingClassDeclarationLineEnd());
    }

    private List<SATDInstanceInFile> getOtherInstancesInCommitFiles(GroupedComment commentToMatch, String curPath) {
        final List<SATDInstanceInFile> allInstances = this.otherDiffEntries.stream()
                .filter(diffEntry -> !diffEntry.getOldPath().equals(curPath))
                .map(diffEntry -> new Pair(diffEntry, this.getCommentsInFileInNewRepository(diffEntry.getNewPath())))
                .flatMap(pair ->
                        ((RepositoryComments)pair.getValue()).getComments().stream()
                                // Only comments that match this comment
                                // TODO - can we apply the same thresholding logic here?
                                .filter(comm -> commentToMatch.getComment().equals(comm.getComment()))
                                // Only comments that were impacted by edits in this commit
                                .filter(comment -> this.getEdits((DiffEntry)pair.getKey()).stream()
                                        .anyMatch(edit -> editImpactedComment(edit, comment, 0, false)))
                                .map(comm -> new SATDInstanceInFile(
                                        ((DiffEntry)pair.getKey()).getNewPath(), comm
                                )))
                .collect(Collectors.toList());
        final List<SATDInstanceInFile> instancesWithSameMethod =
                allInstances.stream()
                        .filter(instanceInFile ->
                            instanceInFile.getComment()
                                    .getContainingMethod()
                                    .equals(commentToMatch.getContainingMethod())
                        ).collect(Collectors.toList());
        // Check if there exists an instance w/ the same method
        if( instancesWithSameMethod.isEmpty() ) {
            // if not, return all instances
            return allInstances;
        }
        // If there is, then return only those instances
        return instancesWithSameMethod;
    }
}
