package se.rit.edu.git.commitlocator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.comments.CommentsCollection;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
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

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class SATDRemovedChangedMovedCommitLocator extends CommitLocator {

    @Override
    public String findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2) {
        try {
            List<RevCommit> commitsBetween = CommitLocatorUtil.getCommitsBetween(gitInstance,
                    gitInstance.getRepository().resolve(v1), gitInstance.getRepository().resolve(v2));
            String curSATDFile = satdInstance.getOldFile();
            for( int i = 1; i < commitsBetween.size(); i++ ) {
                // Get diff for commits
                List<DiffEntry> lde = CommitLocator.getDiffEntries(gitInstance,
                        commitsBetween.get(i-1).getTree(), commitsBetween.get(i).getTree());
                for (DiffEntry de : lde) {
                    String thisCommit = commitsBetween.get(i).getName();
                    if (de.getChangeType().equals(DiffEntry.ChangeType.MODIFY) &&
                            de.getOldPath().equals(curSATDFile)) {
                        // Walk through each Java file
                        TreeWalk thisRepoWalker = getTreeWalker(gitInstance.getRepository(), thisCommit);
                        JavaParser parser = new JavaParser();
                        while ( thisRepoWalker.next() ) {
                            // If the file is the one we care about
                            if( thisRepoWalker.getPathString().equals(curSATDFile) ) {
                                ObjectLoader fileLoader = gitInstance.getRepository()
                                        .open(thisRepoWalker.getObjectId(0));
                                // Parse Java file for comments
                                Optional<CommentsCollection> comments = parser.parse(fileLoader.openStream())
                                        .getCommentsCollection();
                                if (comments.isPresent()) {
                                    // See if the comment that is SATD is still in the file
                                    // FIXME use case: File contains multiple of the same SATD comment
                                    boolean satdIsAbsent = comments.get().getComments().stream().noneMatch(comment ->
                                            comment.getContent()
                                                    .trim()
                                                    .equals(satdInstance.getSATDComment().trim()));
                                    if (satdIsAbsent) {
                                        // TODO check if SATD is changed
                                        // TODO check if the SATD was moved to another file
                                        satdInstance.setResolution(SATDInstance.SATDResolution.SATD_REMOVED);
                                        satdInstance.setNewFile(SATDInstance.FILE_NONE);
                                        satdInstance.setNameOfFileWhenAddressed(curSATDFile);
                                        return thisCommit;
                                    } else {
                                        // SATD still in this commit, so we continue the search
                                        break;
                                    }
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
