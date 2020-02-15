package se.rit.edu.git;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.CommentsCollection;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import se.rit.edu.git.commitlocator.*;
import se.rit.edu.satd.SATDInstance;
import se.rit.edu.satd.detector.SATDDetector;
import se.rit.edu.util.ElapsedTimer;

public class RepositoryCommitReference {

    private String commit;
    private Git gitInstance;
    private String tag;
    private String projectName;
    private int nPulls = 0;
    private Map<String, List<String>> satdOccurrences = null;

    RepositoryCommitReference(Git gitInstance, String projectName, String commitHash, String tag) {
        this.commit = commitHash;
        this.projectName = projectName;
        this.gitInstance = gitInstance;
        this.tag = tag;
    }

    public Map<String, List<String>> getFilesToSAIDOccurrences(SATDDetector detector){

        if( this.satdOccurrences != null ) {
            return this.satdOccurrences;
        }

        ElapsedTimer timer = new ElapsedTimer();
        timer.start();
        Repository repository = null;
        // Checkout a new branch based off the base commit of this instance
        try {
            gitInstance.checkout()
                    .setCreateBranch(true)
                    .setName("SATD_STUDY_temp_" + this.commit + nPulls++ + System.currentTimeMillis())
                    .setStartPoint(this.commit)
                    .addPath("**/*.java")
                    .call();
            repository = gitInstance.getRepository();
        } catch (GitAPIException e) {
            System.err.println("Tried to create branch that already exists");
        }

        // Walk through each Java file
        TreeWalk thisRepoWalker = this.getTreeWalker(repository);
        Map<String, List<String>> filesToSATDMap = new HashMap<>();
        JavaParser parser = new JavaParser();
        try {
            while (thisRepoWalker.next()) {
                String fileName = thisRepoWalker.getPathString();
                ObjectLoader fileLoader = repository.open(thisRepoWalker.getObjectId(0));
                // Parse Java file for comments
                ParseResult parsedFile = parser.parse(fileLoader.openStream());
                if( parsedFile.getCommentsCollection().isPresent() ) {
                    // Check each comment for being SATD
                    CommentsCollection comments = (CommentsCollection)parsedFile.getCommentsCollection().get();
                    List<String> fileSATD = comments.getComments().stream()
                            .filter(comment -> !comment.isJavadocComment())
                            .map(Comment::getContent)
                            .filter(detector::isSATD)
                            .collect(Collectors.toList());
                    filesToSATDMap.put(fileName, fileSATD);
                }
            }
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
        }

        this.satdOccurrences = filesToSATDMap;
        timer.end();
        System.out.println(String.format("Finished finding SATD in %s/%s in %6dms", this.projectName, this.tag, timer.readMS()));
        return filesToSATDMap;
    }

    public String getProjectName() {
        return this.projectName;
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

    private TreeWalk getTreeWalker(Repository repository) {
        TreeWalk treeWalk = new TreeWalk(repository);
        RevWalk revWalk = new RevWalk(repository);
        try {
            RevCommit commit = revWalk.parseCommit(repository.resolve(this.commit));
            treeWalk.addTree(commit.getTree());
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
