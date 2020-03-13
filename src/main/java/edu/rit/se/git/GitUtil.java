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
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;

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
     * @param tree1 a revision tree
     * @param tree2 a revision tree
     * @return A list of all DiffEntries between the two trees
     */
    public static List<DiffEntry> getDiffEntries(Git gitInstance, RevTree tree1, RevTree tree2) {
        try {
            TreeWalk tw = new TreeWalk(gitInstance.getRepository());
            tw.setRecursive(true);
            tw.addTree(tree1);
            tw.addTree(tree2);

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
    public static boolean deletionOccursBetweenLines(Edit edit, int startLine, int endLine) {
        return JavaParseUtil.isRangeBetweenBounds(
                new Range(new Position(edit.getBeginA(), 0), new Position(edit.getEndA(), 0)),
                startLine, endLine
        );
    }

    public static boolean additionOccursBetweenLines(Edit edit, int startLine, int endLine) {
        return JavaParseUtil.isRangeBetweenBounds(
                new Range(new Position(edit.getBeginB(), 0), new Position(edit.getEndB(), 0)),
                startLine, endLine
        );
    }
}
