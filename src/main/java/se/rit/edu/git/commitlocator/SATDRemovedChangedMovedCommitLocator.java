package se.rit.edu.git.commitlocator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import se.rit.edu.satd.SATDInstance;
import se.rit.edu.util.GroupedComment;
import se.rit.edu.util.JavaParseUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.acl.Group;
import java.util.List;
import java.util.stream.Collectors;

public class SATDRemovedChangedMovedCommitLocator extends CommitLocator {

    @Override
    public String findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2) {
        try {
            List<RevCommit> commitsBetween = CommitLocatorUtil.getCommitsBetween(gitInstance,
                    gitInstance.getRepository().resolve(v1), gitInstance.getRepository().resolve(v2));
            String curSATDFile = satdInstance.getOldFile();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            DiffFormatter formatter = new DiffFormatter(outputStream);
            formatter.setRepository(gitInstance.getRepository());
            formatter.setContext(0);
            // Lines of the commit
            int commentStartLine = -1;
            int commentEndLine = -1;
            for( int i = 1; i < commitsBetween.size(); i++ ) {
                // Get diff for commits
                List<DiffEntry> lde = CommitLocator.getDiffEntries(gitInstance,
                        commitsBetween.get(i-1).getTree(), commitsBetween.get(i).getTree());
                for (DiffEntry de : lde) {
                    String thisCommit = commitsBetween.get(i).getName();
                    if (de.getChangeType().equals(DiffEntry.ChangeType.MODIFY) &&
                            de.getOldPath().equals(curSATDFile)) {
                        // Walk through each Java file
                        final TreeWalk thisRepoWalker = getTreeWalker(gitInstance.getRepository(), thisCommit);
                        while ( thisRepoWalker.next() ) {
                            // If the file is the one we care about
                            if( thisRepoWalker.getPathString().equals(curSATDFile) ) {
                                final ObjectLoader fileLoader = gitInstance.getRepository()
                                        .open(thisRepoWalker.getObjectId(0));
                                // See if the comment that is SATD is still in the file
                                // FIXME use case: File contains multiple of the same SATD comment
                                final List<GroupedComment> filteredComments =
                                        JavaParseUtil.parseFileForComments(fileLoader.openStream()).stream()
                                                .filter(comment ->
                                                        compareComments(comment, satdInstance.getSATDComment()))
                                                .collect(Collectors.toList());
                                if (filteredComments.isEmpty()) {
                                    // Check if the SATD comment was modified to another comment
                                    final int curStartLine = commentStartLine;
                                    final int curEndLine = commentEndLine;
                                    List<Edit> thisCommentEdit = formatter.toFileHeader(de).toEditList().stream()
                                            .filter(edit ->
                                                    edit.getBeginA() <= curStartLine &&
                                                    edit.getEndA() >= curEndLine)
                                            .collect(Collectors.toList());
                                    if( !thisCommentEdit.isEmpty() && thisCommentEdit.get(0).getLengthB() == 0 ) {
                                        // We know SATD was removed
                                        satdInstance.setResolution(SATDInstance.SATDResolution.SATD_REMOVED);
                                        satdInstance.setNewFile(SATDInstance.FILE_NONE);
                                    } else if( !thisCommentEdit.isEmpty() && thisCommentEdit.get(0).getLengthB() > 0 ) {
                                        // We the SATD may have only been modified
                                        satdInstance.setResolution(SATDInstance.SATDResolution.SATD_POSSIBLY_REMOVED);
                                        satdInstance.setNewFile(SATDInstance.FILE_UNKNOWN);
                                    }

                                    // TODO can we check if the SATD was moved to another file?
                                    satdInstance.setNameOfFileWhenAddressed(curSATDFile);
                                    return thisCommit;
                                } else {
                                    // Update the lines the comments were found
                                    commentStartLine = filteredComments.get(0).getStartLine();
                                    commentEndLine = filteredComments.get(0).getEndLine();
                                    // SATD still in this commit, so we continue the search
                                    break;
                                }
                            }
                        }
                    }
                    // If the file was renamed, and is the file we're looking for,
                    // Update the name so we can search for the renamed file
                    if( de.getChangeType().equals(DiffEntry.ChangeType.RENAME) &&
                            de.getOldPath().equals(curSATDFile)) {
                        // Start checking for the renamed file
                        curSATDFile = de.getNewPath();
                        satdInstance.setNameOfFileWhenAddressed(curSATDFile);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error resolving commit strings when finding addressed commit in SATDRemovedChangedMovedCommitLocator");
        }
        return SATDInstance.COMMIT_UNKNOWN;
    }

    private static boolean compareComments(GroupedComment comment1, String comment2) {
        return comment1.getComment()
                .replace("\n", " ")
                .trim()
                .equals(
                        comment2.replace("\n", " ")
                            .trim()
                );
    }

    private TreeWalk getTreeWalker(Repository repository, String commit) {
        TreeWalk treeWalk = new TreeWalk(repository);
        RevWalk revWalk = new RevWalk(repository);
        try {
            RevCommit revCommit = revWalk.parseCommit(repository.resolve(commit));
            treeWalk.addTree(revCommit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathSuffixFilter.create(".java"));
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
        } finally {
            revWalk.dispose();
        }
        return treeWalk;
    }

}
