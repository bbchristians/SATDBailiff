package se.rit.edu.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GitUtil {

    public static String getRepoNameFromGithubURI(String githubURI) {
        return githubURI.split(".com/")[1].replace(".git", "");
    }

    // mimic git tag --contains <commit>
    private static Set<Ref> getTagsForCommit(Repository repo,
                                             RevCommit latestCommit) throws Exception {
        final Set<Ref> tags = new HashSet<>();
        final RevWalk walk = new RevWalk(repo);
        walk.reset();
        walk.setTreeFilter(TreeFilter.ANY_DIFF);
        walk.sort(RevSort.TOPO, true);
        walk.sort(RevSort.COMMIT_TIME_DESC, true);
        for (final Ref ref : repo.getTags().values()) {
            final RevObject obj = walk.parseAny(ref.getObjectId());
            final RevCommit tagCommit;
            if (obj instanceof RevCommit) {
                tagCommit = (RevCommit) obj;
            } else if (obj instanceof RevTag) {
                tagCommit = walk.parseCommit(((RevTag) obj).getObject());
            } else {
                continue;
            }
            if (walk.isMergedInto(latestCommit, tagCommit)) {
                tags.add(ref);
            }
        }
        return tags;
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

    // TODO make this a util function as it is duplicate code
    public static boolean editOccursBetweenLines(Edit edit, int startLine, int endLine) {
        return
                // Starts before the start and ends after the start
                (edit.getBeginA() <= startLine && edit.getEndA() >= startLine ) ||
                        // Starts before the end, and ends after the end
                        (edit.getBeginA() <= endLine && edit.getEndA() >= endLine) ||
                        // Starts after the start and ends before the end
                        (edit.getBeginA() >= startLine && edit.getEndA() <= endLine);
    }
}
