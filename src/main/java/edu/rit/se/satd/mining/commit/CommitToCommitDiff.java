package edu.rit.se.satd.mining.commit;

import edu.rit.se.git.RepositoryCommitReference;
import edu.rit.se.satd.SATDInstance;
import edu.rit.se.satd.comment.NullGroupedComment;
import edu.rit.se.util.JavaParseUtil;
import edu.rit.se.util.SimilarityUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import edu.rit.se.git.GitUtil;
import edu.rit.se.satd.comment.GroupedComment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

public class CommitToCommitDiff {

    private Git gitInstance;
    private RevCommit newCommit;
    private DiffFormatter diffFormatter;
    private List<DiffEntry> diffEntries;

    // Store the comments found in each file in the new commit as they are parsed to
    // avoid parsing the same java file multiple times
    private Map<String, List<GroupedComment>> parsedCommentsInNewCommit = new HashMap<>();

    public CommitToCommitDiff(RepositoryCommitReference oldRepo, RepositoryCommitReference newRepo) {
        this.gitInstance = oldRepo.getGitInstance();
        this.newCommit = newRepo.getCommit();
        this.diffEntries = GitUtil.getDiffEntries(this.gitInstance, oldRepo.getCommit().getTree(), this.newCommit.getTree())
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

    public List<SATDInstance> loadDiffsForFile(String filePath, GroupedComment comment, boolean isOldFile) {
        return this.diffEntries.stream()
                .filter(entry -> entry.getOldPath().equals(filePath))
                .map(diffEntry -> isOldFile ?
                        this.getSATDFromDiffOldFile(diffEntry, comment)
                        : this.getSATDDiffFromNewFile(diffEntry, comment))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

    }

    private List<SATDInstance> getSATDFromDiffOldFile(DiffEntry diffEntry, GroupedComment comment) {
        final List<SATDInstance> satd = new ArrayList<>();

        switch (diffEntry.getChangeType()) {
            case RENAME:
                satd.add(
                        new SATDInstance(
                            diffEntry.getOldPath(),
                            diffEntry.getNewPath(),
                            comment,
                            comment,
                            SATDInstance.SATDResolution.FILE_PATH_CHANGED
                ));
                break;
            case DELETE:
                satd.add(
                        new SATDInstance(
                                diffEntry.getOldPath(),
                                diffEntry.getNewPath(),
                                comment,
                                new NullGroupedComment(),
                                SATDInstance.SATDResolution.FILE_REMOVED
                        )
                );
                break;
            case MODIFY:
                try {
                    // Determine if the edit to the file touched the SATD
                    final List<Edit> editsToSATDComment = this.diffFormatter.toFileHeader(diffEntry).toEditList().stream()
                            .filter(edit -> GitUtil.deletionOccursBetweenLines(edit, comment.getStartLine(), comment.getEndLine()))
                            .collect(Collectors.toList());
                    final List<GroupedComment> updatedComments = editsToSATDComment.stream()
                            .flatMap( edit -> this.getCommentsInFileInNewRepository(diffEntry.getNewPath()).stream()
                                    .filter( c -> GitUtil.deletionOccursBetweenLines(edit, c.getStartLine(), c.getEndLine())))
                            .collect(Collectors.toList());
                    if( updatedComments.isEmpty() && !editsToSATDComment.isEmpty() ) {
                        satd.add(
                                new SATDInstance(
                                        diffEntry.getOldPath(),
                                        diffEntry.getNewPath(),
                                        comment,
                                        new NullGroupedComment(),
                                        SATDInstance.SATDResolution.SATD_REMOVED
                                )
                        );
                    } else if( !updatedComments.isEmpty() &&
                            updatedComments.stream()
                                    .map(GroupedComment::getComment)
                                    .noneMatch(comment.getComment()::equals)){
                        satd.addAll(
                                updatedComments.stream()
                                        .map(newComment -> {
                                            // If the comment that was added is similar enough to the old comment
                                            if( SimilarityUtil.commentsAreSimilar(comment, newComment) ) {
                                                    // We know the comment was changed
                                                    return new SATDInstance(
                                                            diffEntry.getOldPath(),
                                                            diffEntry.getNewPath(),
                                                            comment,
                                                            newComment,
                                                            SATDInstance.SATDResolution.SATD_CHANGED);
                                            } else {
                                                    // We know the comment was removed
                                                    return new SATDInstance(
                                                            diffEntry.getOldPath(),
                                                            diffEntry.getNewPath(),
                                                            comment,
                                                            new NullGroupedComment(),
                                                            SATDInstance.SATDResolution.SATD_REMOVED);
                                            }
                                        })
                                        .collect(Collectors.toList()));
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
                                diffEntry.getOldPath(),
                                diffEntry.getNewPath(),
                                new NullGroupedComment(),
                                comment,
                                SATDInstance.SATDResolution.SATD_ADDED
                        )
                );
                break;
            case MODIFY:
                // Determine if the SATD was actually added
                //  This filters out merge commits
                // Determine if the edit to the file touched the SATD
                try {
                    final List<Edit> editsToSATDComment = this.diffFormatter.toFileHeader(diffEntry).toEditList().stream()
                            .filter(edit -> GitUtil.additionOccursBetweenLines(edit, comment.getStartLine(), comment.getEndLine()))
                            .collect(Collectors.toList());
                    final List<GroupedComment> updatedComments = editsToSATDComment.stream()
                            .flatMap(edit -> this.getCommentsInFileInNewRepository(diffEntry.getNewPath()).stream()
                                    .filter(c -> GitUtil.deletionOccursBetweenLines(edit, c.getStartLine(), c.getEndLine())))
                            .collect(Collectors.toList());
                    if( updatedComments.contains(comment) ) {
                        satd.add(
                                new SATDInstance(
                                        diffEntry.getOldPath(),
                                        diffEntry.getNewPath(),
                                        new NullGroupedComment(),
                                        comment,
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

    private List<GroupedComment> getCommentsInFileInNewRepository(String fileName) {
        if( this.parsedCommentsInNewCommit.containsKey(fileName) ) {
            // Return the value if already parsed
            return this.parsedCommentsInNewCommit.get(fileName);
        }
        // Otherwise, parse and then store the value
        try {
            List<GroupedComment> commentsInFile = JavaParseUtil.parseFileForComments(this.getFileContents(fileName));
            this.parsedCommentsInNewCommit.put(fileName, commentsInFile);
            return commentsInFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

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
