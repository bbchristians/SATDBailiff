package edu.rit.se.satd.mining.commit;

import edu.rit.se.git.GitUtil;
import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.satd.comment.GroupedComment;
import edu.rit.se.satd.comment.NullGroupedComment;
import edu.rit.se.satd.comment.RepositoryComments;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.model.SATDInstanceInFile;
import edu.rit.se.util.JavaParseUtil;
import edu.rit.se.util.KnownParserException;
import edu.rit.se.util.SimilarityUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.jgit.diff.DiffEntry.DEV_NULL;

public class CommitToCommitDiff {

    private Git gitInstance;
    private RevCommit newCommit;
    private DiffFormatter diffFormatter;
    private List<DiffEntry> diffEntries;

    // Store the comments found in each file in the new commit as they are parsed to
    // avoid parsing the same java file multiple times
    private Map<String, List<GroupedComment>> parsedCommentsInNewCommit = new HashMap<>();

    public CommitToCommitDiff(RepositoryCommitReference oldRepo, RepositoryCommitReference newRepo) {
        this.gitInstance = newRepo.getGitInstance();
        this.newCommit = newRepo.getCommit();
        this.diffEntries = GitUtil.getDiffEntries(this.gitInstance, oldRepo.getCommit(), this.newCommit)
                .stream()
                .filter(diffEntry -> diffEntry.getOldPath().endsWith(".java") || diffEntry.getNewPath().endsWith(".java"))
                .collect(Collectors.toList());
        this.diffFormatter = this.getFormatter(this.gitInstance);
    }

    public List<String> getModifiedFilesNew() {
        return this.diffEntries.stream()
                .map(DiffEntry::getNewPath)
                .collect(Collectors.toList());
    }

    public List<String> getModifiedFilesOld() {
        return this.diffEntries.stream()
                .map(DiffEntry::getOldPath)
                .collect(Collectors.toList());
    }

    public List<SATDInstance> loadDiffsForOldFile(String oldFile, GroupedComment comment) {
        return this.diffEntries.stream()
                .filter(entry -> entry.getOldPath().equals(oldFile))
                .map(diffEntry -> this.getSATDFromDiffOldFile(diffEntry, comment))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

    }

    public List<SATDInstance> loadDiffsForNewFile(String newFile, GroupedComment comment) {
        return this.diffEntries.stream()
                .filter(entry -> entry.getNewPath().equals(newFile))
                .map(diffEntry -> this.getSATDDiffFromNewFile(diffEntry, comment))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    // TODO Can the SATD ID be set here?
    private List<SATDInstance> getSATDFromDiffOldFile(DiffEntry diffEntry, GroupedComment comment) {
        final List<SATDInstance> satd = new ArrayList<>();

        switch (diffEntry.getChangeType()) {
            case RENAME:
                final RepositoryComments comInNewRepository =
                        this.getCommentsInFileInNewRepository(diffEntry.getNewPath());
                final GroupedComment newComment = comInNewRepository.getComments().stream()
                        .filter(nc -> nc.getComment().equals(comment.getComment()))
                        // TODO how do we account for multiple SATD Instances in the same file with identical comments
                        //  In the same class and method?
                        .filter(nc -> nc.getContainingMethod().equals(comment.getContainingMethod()))
                        .findFirst()
                        .orElse(new NullGroupedComment());
                // If the SATD couldn't be found in the new file, then it must have been removed
                final SATDInstance.SATDResolution resolution = ( newComment instanceof NullGroupedComment ) ?
                        SATDInstance.SATDResolution.SATD_REMOVED : SATDInstance.SATDResolution.FILE_PATH_CHANGED;
                satd.add(
                        new SATDInstance(
                                new SATDInstanceInFile(diffEntry.getOldPath(), comment),
                                new SATDInstanceInFile(diffEntry.getNewPath(), newComment),
                                resolution
                ));
                break;
            case DELETE:
                satd.add(
                        new SATDInstance(
                                new SATDInstanceInFile(diffEntry.getOldPath(), comment),
                                new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
                                SATDInstance.SATDResolution.FILE_REMOVED
                        )
                );
                break;
            case MODIFY:
                try {
                    // get the edits to the file, and the deletions to the SATD we're concerned about
                    final List<Edit> editsToFile = this.diffFormatter.toFileHeader(diffEntry).toEditList();
                    final List<Edit> editsToSATDComment = editsToFile.stream()
                            .filter(edit -> GitUtil.editOccursInOldFileBetween(edit, comment.getStartLine(), comment.getEndLine()))
                            .collect(Collectors.toList());
                    // Find the comments in the new repository version
                    final RepositoryComments commentsInNewRepository = this.getCommentsInFileInNewRepository(diffEntry.getNewPath());
                    // Find the comments created by deleting
                    final List<GroupedComment> updatedComments = editsToSATDComment.stream()
                            .flatMap( edit -> commentsInNewRepository.getComments().stream()
                                    .filter( c -> GitUtil.editOccursInNewFileBetween(edit, c.getStartLine(), c.getEndLine())))
                            .collect(Collectors.toList());
                    // If changes were made to the SATD comment, and now the comment is missing
                    if( updatedComments.isEmpty() && !editsToSATDComment.isEmpty()) {
                        satd.add(
                                new SATDInstance(
                                        new SATDInstanceInFile(diffEntry.getOldPath(), comment),
                                        new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
                                        SATDInstance.SATDResolution.SATD_REMOVED
                                )
                        );
                        return satd;
                    }
                    // If an updated comment was found, and it is not identical to the old comment
                    if( !updatedComments.isEmpty() &&
                            updatedComments.stream()
                                    .map(GroupedComment::getComment)
                                    .noneMatch(comment.getComment()::equals)){
                        satd.addAll(
                                updatedComments.stream()
                                        .map(nc -> {
                                            // If the comment that was added is similar enough to the old comment
                                            if( SimilarityUtil.commentsAreSimilar(comment, nc) ) {
                                                    // We know the comment was changed
                                                    return new SATDInstance(
                                                            new SATDInstanceInFile(diffEntry.getOldPath(), comment),
                                                            new SATDInstanceInFile(diffEntry.getNewPath(), nc),
                                                            SATDInstance.SATDResolution.SATD_CHANGED);
                                            } else {
                                                    // We know the comment was removed, and the one that was added
                                                    // was a different comment
                                                    return new SATDInstance(
                                                            new SATDInstanceInFile(diffEntry.getOldPath(), comment),
                                                            new SATDInstanceInFile(diffEntry.getNewPath(), new NullGroupedComment()),
                                                            SATDInstance.SATDResolution.SATD_REMOVED);
                                            }
                                        })
                                        .collect(Collectors.toList()));
                        return satd;
                    }
                    // If the comment was updated and they are identical to the old comment
                    if( editsToFile.stream().anyMatch( edit ->
                            GitUtil.editOccursInOldFileBetween(edit,
                                    comment.getContainingClassDeclarationLine(),
                                    comment.getContainingClassDeclarationLine()) ||
                            GitUtil.editOccursInOldFileBetween(edit,
                                    comment.getContainingMethodDeclarationLine(),
                                    comment.getContainingMethodDeclarationLine())) ) {
                        // Check to see if the name of the containing method/class were updated
                        commentsInNewRepository.getComments().stream()
                                .filter(c -> c.getComment().equals(comment.getComment()))
                                .filter(c -> !c.getContainingClass().equals(comment.getContainingClass()) ||
                                        !c.getContainingMethod().equals(comment.getContainingMethod()))
                                // Determine if the comment's method or class was renamed
                                .filter(c -> editsToFile.stream().anyMatch( edit ->
                                        GitUtil.editOccursInNewFileBetween(edit,
                                                c.getContainingClassDeclarationLine(),
                                                c.getContainingClassDeclarationLine()) ||
                                        GitUtil.editOccursInNewFileBetween(edit,
                                                c.getContainingMethodDeclarationLine(),
                                                c.getContainingMethodDeclarationLine())))
                                .map(nc -> new SATDInstance(
                                        new SATDInstanceInFile(diffEntry.getOldPath(), comment),
                                        new SATDInstanceInFile(diffEntry.getNewPath(), nc),
                                        SATDInstance.SATDResolution.SATD_CHANGED))
                                .findFirst()
                                .ifPresent(satd::add);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                break;
        }
        return satd;
    }

    private List<SATDInstance> getSATDDiffFromNewFile(DiffEntry diffEntry, GroupedComment comment) {
        final List<SATDInstance> satd = new ArrayList<>();

        switch (diffEntry.getChangeType()) {
            case ADD:
                satd.add(
                        new SATDInstance(
                                new SATDInstanceInFile(DEV_NULL, new NullGroupedComment()),
                                new SATDInstanceInFile(diffEntry.getNewPath(), comment),
                                SATDInstance.SATDResolution.SATD_ADDED
                        )
                );
                break;
            case MODIFY:
                // Determine if the edit to the file touched the SATD
                try {
                    final List<Edit> editsToSATDComment = this.diffFormatter.toFileHeader(diffEntry).toEditList().stream()
                            .filter(edit -> GitUtil.editOccursInNewFileBetween(edit, comment.getStartLine(), comment.getEndLine()))
                            .collect(Collectors.toList());
                    if( !editsToSATDComment.isEmpty() ) {
                        satd.add(
                                new SATDInstance(
                                        new SATDInstanceInFile(DEV_NULL, new NullGroupedComment()),
                                        new SATDInstanceInFile(diffEntry.getNewPath(), comment),
                                        SATDInstance.SATDResolution.SATD_ADDED
                                )
                        );
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                break;
        }

        return satd;
    }

    private DiffFormatter getFormatter(Git gitInstance) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(gitInstance.getRepository());
        formatter.setContext(0);
        return formatter;
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
        final TreeWalk treeWalk = GitUtil.getTreeWalker(this.gitInstance, this.newCommit);
        while (treeWalk.next()) {
            if( treeWalk.getPathString().equals(fileName) ) {
                return this.gitInstance.getRepository().open(treeWalk.getObjectId(0)).openStream();
            }
        }
        System.err.println("File not found -- this SHOULD NOT happen!");
        // Return a blank stream if the file was not found
        return new InputStream() {
            @Override
            public int read() {
                return -1;  // end of stream
            }
        };
    }

}
