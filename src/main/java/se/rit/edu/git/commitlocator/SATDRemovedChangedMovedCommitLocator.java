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
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import se.rit.edu.satd.SATDInstance;
import se.rit.edu.util.GroupedComment;
import se.rit.edu.util.JavaParseUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SATDRemovedChangedMovedCommitLocator extends CommitLocator {

    @Override
    public void findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2) {
        try {
            final List<RevCommit> commitsBetween = CommitLocatorUtil.getCommitsBetween(gitInstance,
                    gitInstance.getRepository().resolve(v1), gitInstance.getRepository().resolve(v2));
            final DiffFormatter formatter = this.getFormatter(gitInstance);
            // Cur file to search for, noted in case of file rename
            String fileToSearchFor = satdInstance.getOldFile();
            // Lines of the commit
            int commentStartLine = satdInstance.getStartLineNumberOldFile();
            int commentEndLine = satdInstance.getEndLineNumberOldFile();
            for( int i = 1; i < commitsBetween.size(); i++ ) {
                final String thisCommit = commitsBetween.get(i).getName();

                final String fileToSearchForBeforeRename = fileToSearchFor;
                // Get all diffs in the commit for the file we're looking for
                final List<DiffEntry> lde = CommitLocator.getDiffEntries(
                        gitInstance,
                        commitsBetween.get(i-1).getTree(),
                        commitsBetween.get(i).getTree()).stream().
                        filter(de -> de.getOldPath().equals(fileToSearchForBeforeRename))
                        .collect(Collectors.toList());

                // If the file was renamed, and is the file we're looking for,
                // Update the name so we can search for the renamed file
                final Optional<DiffEntry> renameEntry = lde.stream()
                        .filter(de -> de.getChangeType().equals(DiffEntry.ChangeType.RENAME))
                        .findFirst();
                if( renameEntry.isPresent() ) {
                    // Start checking for the renamed file
                    fileToSearchFor = renameEntry.get().getNewPath();
                    satdInstance.setNameOfFileWhenAddressed(fileToSearchFor);
                }

                final String fileToSearchForAfterRename = fileToSearchFor;

                // If file was modified
                final List<DiffEntry> ldeModifies = lde.stream()
                        .filter(de -> de.getChangeType().equals(DiffEntry.ChangeType.MODIFY))
                        .collect(Collectors.toList());
                if( !ldeModifies.isEmpty() ) {
                    // DiffEntry does not contain the file contents of the changed file,
                    // So we must manually walk through the directory until we find
                    // the changed file
                    // Walk through each Java file
                    final TreeWalk thisRepoWalker = getTreeWalker(gitInstance.getRepository(), thisCommit);
                    while ( thisRepoWalker.next() ) {
                        // If the file is the one we care about
                        if( thisRepoWalker.getPathString().equals(fileToSearchForAfterRename) ) {
                            final ObjectLoader fileLoader = gitInstance.getRepository()
                                    .open(thisRepoWalker.getObjectId(0));
                            // See if the comment that is SATD is still in the file
                            // FIXME use case: File contains multiple of the same SATD comment
                            final List<GroupedComment> filteredComments =
                                    JavaParseUtil.parseFileForComments(fileLoader.openStream()).stream()
                                            .filter(comment ->
                                                    compareComments(comment, satdInstance.getCommentOld()))
                                            .collect(Collectors.toList());
                            // If this commit did not contain an identical SATD String to the one we're
                            // searching for
                            if (filteredComments.isEmpty()) {
                                // Check if the SATD comment was modified rather than removed
                                final int curStartLine = commentStartLine;
                                final int curEndLine = commentEndLine;
                                final List<Edit> editsInsideSATDBlock = ldeModifies.stream().map(de -> {
                                            try {
                                                return formatter.toFileHeader(de);
                                            } catch (IOException e) {
                                                throw new UncheckedIOException(e);
                                            }
                                        })
                                        .map(FileHeader::toEditList)
                                        .flatMap(Collection::stream) // Here we have all edits in the file
                                        .filter(edit -> editOccursBetweenLines(edit, curStartLine, curEndLine))
                                        .collect(Collectors.toList());
                                final int numLinesAddedInSATDRange = editsInsideSATDBlock.stream()
                                        .mapToInt(Edit::getLengthB)
                                        .sum();

                                // If no lines were added where SATD comment was removed
                                // FIXME it is possible that multi-line SATD could have 1 line removed --
                                //  and still be SATD -- filter EditList to find what the boundaries of the edits were
                                if( !editsInsideSATDBlock.isEmpty() && numLinesAddedInSATDRange == 0 ) {
                                    // We know some SATD was removed, and nothing was added
                                    satdInstance.setResolution(SATDInstance.SATDResolution.SATD_REMOVED);
                                    satdInstance.setNewFile(SATDInstance.FILE_NONE);
                                }
                                // If at least one line was added where SATD comment was removed
                                else if( !editsInsideSATDBlock.isEmpty() && numLinesAddedInSATDRange > 0 ) {
                                    // We the SATD may have only been modified
                                    satdInstance.setResolution(SATDInstance.SATDResolution.SATD_POSSIBLY_REMOVED);
                                    satdInstance.setNewFile(fileToSearchForAfterRename);
                                }
                                // If no changes were made to the SATD comment, but the file was changed
                                else if( !fileToSearchForBeforeRename.equals(fileToSearchForAfterRename) ) {
                                    satdInstance.setResolution(SATDInstance.SATDResolution.FILE_PATH_CHANGED);
                                    satdInstance.setNewFile(fileToSearchForAfterRename);
                                }

                                // TODO can we check if the SATD was moved to another file?
                                satdInstance.setNameOfFileWhenAddressed(fileToSearchFor);
                                satdInstance.setCommitRemoved(thisCommit);
                                return;
                            }
                            // If the identical comment was still present in the file
                            else {
                                // Update the lines the comments were found
                                commentStartLine = filteredComments.get(0).getStartLine();
                                commentEndLine = filteredComments.get(0).getEndLine();
                                // SATD still in this commit, so we continue the search
                                break;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error resolving commit strings when finding addressed commit in SATDRemovedChangedMovedCommitLocator");
            satdInstance.setCommitRemoved(SATDInstance.ERROR_FINDING_COMMIT);
            satdInstance.setResolution(SATDInstance.SATDResolution.ERROR_UNKNOWN);
        }
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

    private DiffFormatter getFormatter(Git gitInstance) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(gitInstance.getRepository());
        formatter.setContext(0);
        return formatter;
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

    private static boolean editOccursBetweenLines(Edit edit, int startLine, int endLine) {
        return
                // Starts before the start and ends after the start
                (edit.getBeginA() <= startLine && edit.getEndA() >= startLine ) ||
                // Starts before the end, and ends after the end
                (edit.getBeginA() <= endLine && edit.getEndA() >= endLine) ||
                // Starts after the start and ends before the end
                (edit.getBeginA() >= startLine && edit.getEndA() <= endLine);
    }

}
