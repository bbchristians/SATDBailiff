package se.rit.edu.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import se.rit.edu.satd.detector.SATDDetector;
import se.rit.edu.util.ElapsedTimer;
import se.rit.edu.util.GroupedComment;
import se.rit.edu.util.JavaParseUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RepositoryCommitReference {

    private String commit;
    private Git gitInstance;
    private String tag;
    private String projectName;
    private String projectURI;
    private Map<String, List<GroupedComment>> satdOccurrences = null;

    private ElapsedTimer timer = null;

    RepositoryCommitReference(Git gitInstance, String projectName, String projectURI, String commitHash, String tag) {
        this.commit = commitHash;
        this.projectName = projectName;
        this.projectURI = projectURI;
        this.gitInstance = gitInstance;
        this.tag = tag;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public String getProjectURI() {
        return this.projectURI;
    }

    public String getTag() {
        return this.tag;
    }

    public String getCommit() {
        return this.commit;
    }

    public Git getGitInstance() {
        return this.gitInstance;
    }

    public Map<String, List<GroupedComment>> getFilesToSAIDOccurrences(SATDDetector detector){

        if( this.satdOccurrences != null ) {
            return this.satdOccurrences;
        }

        this.startSATDParseTimer();

        final TreeWalk thisRepoWalker = this.getTreeWalker();
        final Map<String, List<GroupedComment>> filesToSATDMap = new HashMap<>();
        try {
            // Walk through each Java file in the repository at the time of the commit
            while (thisRepoWalker.next()) {
                // Get loader to load file contents into memory
                final ObjectLoader fileLoader = this.gitInstance.getRepository()
                        .open(thisRepoWalker.getObjectId(0));
                // Parse Java file for SATD and add it to the map
                filesToSATDMap.put(
                        thisRepoWalker.getPathString(),
                        JavaParseUtil.parseFileForComments(fileLoader.openStream()).stream()
                                .filter(groupedComment -> detector.isSATD(groupedComment.getComment()))
                                .collect(Collectors.toList())
                );
            }
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
            e.printStackTrace();
        }

        this.endSATDParseTimer();
        // Store a reference to be returned later to avoid parsing more than once
        this.satdOccurrences = filesToSATDMap;
        return filesToSATDMap;
    }

    /**
     * @return a TreeWalk instance for the repository at the given commit
     */
    private TreeWalk getTreeWalker() {
        TreeWalk treeWalk = new TreeWalk(this.gitInstance.getRepository());
        RevWalk revWalk = new RevWalk(this.gitInstance.getRepository());
        try {
            RevCommit commit = revWalk.parseCommit(this.gitInstance.getRepository().resolve(this.commit));
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathSuffixFilter.create(".java"));
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
            e.printStackTrace();
        } finally {
            revWalk.dispose();
        }
        return treeWalk;
    }

    /**
     * Overwrites and starts a time to record the time it takes to locate SATD In the repository
     * at the given commit
     */
    private void startSATDParseTimer() {
        this.timer = new ElapsedTimer();
        this.timer.start();
    }

    /**
     * Ends the timer, and reports the time it took to parse the SATD in the repository
     */
    private void endSATDParseTimer() {
        if( this.timer != null ) {
            this.timer.end();
            System.out.println(String.format("Finished finding SATD in %s/%s in %,dms",
                    this.projectName, this.tag, this.timer.readMS()));
        }
    }
}
