package se.rit.edu.git.commitlocator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import se.rit.edu.satd.SATDInstance;

import java.io.IOException;
import java.util.List;

public class FileRemovedOrRenamedCommitLocator implements CommitLocator {

    @Override
    public String findCommitIntroduced(Git gitInstance, SATDInstance satdInstance, String v1, String v2) {
        // TODO git blame created string
        return null;
    }

    /**
     *
     * @param gitInstance gitInstance
     * @param satdInstance SATD Instance object
     * @param v1 first commit bound for finding SATD
     * @param v2 second commit bound for finding SATD
     * @return a String of the commit hash where the SATD was removed or renamed
     *
     * Code partially copied from https://stackoverflow.com/questions/17296278/jgit-detect-rename-in-working-copy
     */
    @Override
    public String findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2) {
        try {
            List<RevCommit> commitsBetween = CommitLocatorUtil.getCommitsBetween(gitInstance,
                    gitInstance.getRepository().resolve(v1), gitInstance.getRepository().resolve(v2));
            String fileToFindDeletion = satdInstance.getOldFile();
            String commitIfRenamed = null;
            for( int i = 1; i < commitsBetween.size(); i++ ) {
                TreeWalk tw = new TreeWalk(gitInstance.getRepository());
                tw.setRecursive(true);
                tw.addTree(commitsBetween.get(i-1).getTree());
                tw.addTree(commitsBetween.get(i).getTree());

                RenameDetector rd = new RenameDetector(gitInstance.getRepository());
                rd.addAll(DiffEntry.scan(tw));

                List<DiffEntry> lde = rd.compute(tw.getObjectReader(), null);
                for (DiffEntry de : lde) {
                    // If the file was removed and is the file we're looking for,
                    // Then return the commit which removed the file
                    if (de.getChangeType().equals(DiffEntry.ChangeType.DELETE) &&
                        de.getOldPath().equals(fileToFindDeletion)) {
                        satdInstance.setNameOfFileWhenAddressed(fileToFindDeletion);
                        satdInstance.setResolution(SATDInstance.SATDResolution.FILE_REMOVED);
                        return commitsBetween.get(i).getName();
                    }
                    // If the file was renamed, and is the file we're looking for,
                    // Update the name so we can find when the renamed file was removed
                    // Keep note of the last commit that renamed the file, to be returned
                    // if we do not find that the file was removed
                    if( de.getChangeType().equals(DiffEntry.ChangeType.RENAME) &&
                        de.getOldPath().equals(fileToFindDeletion)) {
                        satdInstance.setResolution(SATDInstance.SATDResolution.FILE_RENAMED);
                        // Name gets set here in the case of rename being the only operation
                        satdInstance.setNameOfFileWhenAddressed(fileToFindDeletion);
                        fileToFindDeletion = de.getNewPath();
                        commitIfRenamed = commitsBetween.get(i).getName();
                    }
                }
            }
            if( commitIfRenamed != null ) {
                satdInstance.setNewFile(fileToFindDeletion);
            }
            return commitIfRenamed;
        } catch (CorruptObjectException e) {
            System.err.println("Corrupted tree when parsing files in repository.");
        } catch (IOException e) {
            System.err.println("Error when diffing files");
        }
        return null;
    }
}