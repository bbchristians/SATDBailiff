package edu.rit.se.git;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import edu.rit.se.util.JavaParseUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GitUtil {

    /**
     * Parses a repository name from a GitHub URI
     * @param githubURI the Github URI, like "https://github.com/bbchristians/SATDMiner"
     * @return a parsed URI, like "bbchristians/SATDMiner"
     */
    public static String getRepoNameFromGithubURI(String githubURI) {
        return githubURI.split(".com/")[1].replace(".git", "");
    }

    /**
     * @return a TreeWalk instance for the repository at the given commit
     */
    public static TreeWalk getTreeWalker(Git gitInstance, RevCommit commit) {
        TreeWalk treeWalk = new TreeWalk(gitInstance.getRepository());
        try {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathSuffixFilter.create(".java"));
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
            e.printStackTrace();
        }
        return treeWalk;
    }

    /**
     * Gets all diffs between two revision trees
     * @param gitInstance the Git instance the revisions take place within
     * @param commit1 a RevCommit
     * @param commit2 a RevCommit
     * @return A list of all DiffEntries between the two trees
     */
    public static List<DiffEntry> getDiffEntries(Git gitInstance, RevCommit commit1, RevCommit commit2) {
        try {
            TreeWalk tw = new TreeWalk(gitInstance.getRepository());
            tw.setRecursive(true);
            if( commit1 != null ) {
                tw.addTree(commit1.getTree());
            } else {
                tw.addTree(new EmptyTreeIterator());
            }
            if( commit2 != null ) {
                tw.addTree(commit2.getTree());
            } else {
                tw.addTree(new EmptyTreeIterator());
            }

            RenameDetector rd = new RenameDetector(gitInstance.getRepository());
            rd.addAll(DiffEntry.scan(tw));

            return rd.compute(tw.getObjectReader(), null);
        } catch (IOException e) {
            System.err.println("Error diffing trees.");
        }
        return new ArrayList<>();
    }

    /**
     * Determines if an edit occurs between two line bounds
     * @param edit the edit object
     * @param startLine the start line bound
     * @param endLine the end line bound
     * @return True if the edit touches any lines between the bounds (inclusive), else False
     */
    public static boolean editOccursInOldFileBetween(Edit edit, int startLine, int endLine) {
        return JavaParseUtil.isRangeBetweenBounds(
                new Range(new Position(edit.getBeginA(), 0), new Position(edit.getEndA(), 0)),
                startLine, endLine
        );
    }

    public static boolean editOccursInNewFileBetween(Edit edit, int startLine, int endLine) {
        return JavaParseUtil.isRangeBetweenBounds(
                new Range(new Position(edit.getBeginB(), 0), new Position(edit.getEndB(), 0)),
                startLine, endLine
        );
    }

    public static File getTempReferenceToFile(Git gitInstance, RevCommit commit, String filePath) {
        if( commit == null || filePath.equals("/dev/null") ) {
            File devNullFile = new File("tmp/dev/null/null.java");
            if( devNullFile.exists() ) {
                return devNullFile;
            }
            devNullFile.getParentFile().mkdirs();
            try {
                FileOutputStream fOut = new FileOutputStream(devNullFile);
                fOut.write(new byte[0]);
                fOut.close();
                return devNullFile;
            } catch (IOException e) {
                return null;
            }
        }
        TreeWalk walker = getTreeWalker(gitInstance, commit);
        walker.setFilter(PathFilter.create(filePath));
        try {
            if (walker.next()) {
                File tempFile = new File(String.format("tmp/%s/%s", commit.getName(), filePath));
                tempFile.getParentFile().mkdirs();
                ObjectLoader loader = gitInstance.getRepository().open(walker.getObjectId(0));
                FileOutputStream out = new FileOutputStream(tempFile);
                out.write(loader.getBytes());
                out.close();
                return tempFile;
            }
        } catch (IOException e) {
            System.err.println("Error parsing git for file.");
        }
        return null;
    }
}
