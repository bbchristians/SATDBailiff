package git;

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
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import satd_detector.core.utils.SATDDetector;

public class GitRepository {

    private String commit;
    private String gitPath;
    private Git gitInstance;

    private static boolean ONLY_JAVA_FILES = true;

    public GitRepository(Git gitInstance, String gitPath, String name, String url, String commitHash) {
        this.commit = commitHash;
        this.gitInstance = gitInstance;
        this.gitPath = gitPath;
    }

    public SATDDifference diffAgainstNewerRepository(GitRepository newerRepository, SATDDetector detector) {

        Map<String, Integer> olderSATD = this.getFilesToSAIDOccurrences(detector);
        System.out.println("Old Repo scan finished");
        Map<String, Integer> newerSATD = newerRepository.getFilesToSAIDOccurrences(detector);
        System.out.println("New Repo scan finished");

        Map<String, String> fileMapping = RepositoryFileMapping.getFileMapping(olderSATD.keySet(), newerSATD.keySet());
        System.out.println("File Mapping Finished");

        SATDDifference difference = new SATDDifference();

        for( String oldKey : olderSATD.keySet() ) {
            String newKey = fileMapping.get(oldKey);
            difference.addTotalSATD(olderSATD.get(oldKey));
            if( newKey.equals(RepositoryFileMapping.NOT_FOUND) ) {
                difference.addFileRemovedSATD(olderSATD.get(oldKey));
            } else {
                int oldSATDCount = olderSATD.get(oldKey);
                int newSATDCount = newerSATD.get(newKey);
                if( oldSATDCount > newSATDCount ) {
                    difference.addAddressedSATD(oldSATDCount - newSATDCount);
                }
            }
        }
        return difference;
    }

    public Map<String, Integer> getFilesToSAIDOccurrences(SATDDetector detector){
        Repository repository = null;
        try {
            gitInstance.checkout()
                    .setCreateBranch(true)
                    .setName("SATD_STUDY_temp_" + this.commit)
                    .setStartPoint(this.commit)
                    .call();
            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
            repository = repositoryBuilder.setGitDir(new File(this.gitPath))
                    .readEnvironment() // scan environment GIT_* variables
                    .findGitDir() // scan up the file system tree
                    .setMustExist(true)
                    .build();
        } catch (IOException e) {
            System.err.println("Error reading from git in commit: " + this.commit);
        } catch (GitAPIException e) {
            System.err.println("Git API Exception");
        }

        TreeWalk thisRepoWalker = this.getTreeWalker(repository );
        Map<String, Integer> filesToSATDMap = new HashMap<>();
        JavaParser parser = new JavaParser();
        try {
            while (thisRepoWalker.next()) {
                String fileName = thisRepoWalker.getPathString();
                ObjectLoader fileLoader = repository.open(thisRepoWalker.getObjectId(0));
                // Parse Java file for comments
                ParseResult parsedFile = parser.parse(fileLoader.openStream());
                if( parsedFile.getCommentsCollection().isPresent() ) {
                    CommentsCollection comments = (CommentsCollection)parsedFile.getCommentsCollection().get();
                    // Count SATD occurrences in file
                    Integer totalSATD = 0;
                    for(Comment comment : comments.getComments()) {
                        if( !comment.isJavadocComment() && detector.isSATD(comment.getContent()) ) {
                            System.out.println("---------\n" + comment.getContent());
                            totalSATD++;
                        }
                    }
                    filesToSATDMap.put(fileName, totalSATD);
                }
            }
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
        }

        return filesToSATDMap;
    }

    private TreeWalk getTreeWalker(Repository repository) {
        TreeWalk treeWalk = new TreeWalk(repository);
        RevWalk revWalk = new RevWalk(repository);
        try {
            RevCommit commit = revWalk.parseCommit(repository.resolve(this.commit));
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            if (ONLY_JAVA_FILES) {
                treeWalk.setFilter(PathSuffixFilter.create(".java"));
            }
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
