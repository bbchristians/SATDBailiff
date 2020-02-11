package se.rit.edu.git.commitlocator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CommitLocatorUtil {

    /**
     * @param gitInstance A reference to the git repository
     * @param firstCommit The first occurring commit (chronologically)
     * @param lastCommit The second occurring commit (chronologically)
     * @return An ordered list of commits between (inclusive) the two given commits
     */
    public static List<RevCommit> getCommitsBetween(Git gitInstance, ObjectId firstCommit, ObjectId lastCommit) {
        try {
            // Use git log to get all commits between, excluding the first commit
            List<RevCommit> commitsBetween =  StreamSupport.stream(
                    gitInstance.log()
                        .addRange(firstCommit, lastCommit)
                        .call()
                        .spliterator()
            , false).collect(Collectors.toList());
            // Find the commit object for the first commit
            RevWalk rw = new RevWalk(gitInstance.getRepository());
            // Build the list of commits
            List<RevCommit> allCommits = new ArrayList<>();
            allCommits.add(rw.parseCommit(firstCommit));
            Collections.reverse(commitsBetween);
            allCommits.addAll(commitsBetween);
            return allCommits;
        } catch (IOException e) {
            System.err.println("IOException while getting commits from remote.");
        } catch (GitAPIException e) {
            System.err.println("Git API exception while getting commits from remote.");
        }
        return new ArrayList<>();
    }
}
