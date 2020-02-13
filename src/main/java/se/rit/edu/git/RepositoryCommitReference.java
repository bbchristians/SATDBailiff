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
import satd_detector.core.utils.SATDDetector;
import se.rit.edu.git.commitlocator.*;
import se.rit.edu.satd.SATDDifference;
import se.rit.edu.satd.SATDInstance;
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

    public SATDDifference diffAgainstNewerRepository(RepositoryCommitReference newerRepository, SATDDetector detector) {

        Map<String, List<String>> olderSATD = this.getFilesToSAIDOccurrences(detector);
        Map<String, List<String>> newerSATD = newerRepository.getFilesToSAIDOccurrences(detector);

        Map<String, String> fileMapping = RepositoryFileMapping.getFileMapping(olderSATD.keySet(), newerSATD.keySet());

        SATDDifference difference = new SATDDifference(this.projectName, this.tag, newerRepository.tag);

        for( String oldKey : olderSATD.keySet() ) {
            String newKey = fileMapping.get(oldKey);
            if( newKey.equals(RepositoryFileMapping.NOT_FOUND) ) {
                difference.addFileRemovedSATD(olderSATD.get(oldKey).stream()
                        .map(comment -> new SATDInstance(oldKey, "dev/null", comment))
                        .collect(Collectors.toList()));
            } else {
                // See which strings in each file are present here
                final List<MappedSATDComment> oldSATDStrings = olderSATD.get(oldKey).stream()
                        .map(MappedSATDComment::new)
                        .collect(Collectors.toList());
                final List<MappedSATDComment> newSATDStrings = newerSATD.get(newKey).stream()
                        .map(MappedSATDComment::new)
                        .collect(Collectors.toList());

                newSATDStrings.forEach(newerSATDMapping -> {
                    oldSATDStrings.forEach(olderSATDMapping -> {
                        if( !olderSATDMapping.isMapped() && !newerSATDMapping.isMapped() &&
                                olderSATDMapping.getComment().equals(newerSATDMapping.getComment()) ) {
                            olderSATDMapping.setMapped();
                            newerSATDMapping.setMapped();
                        }
                    });
                });
                // SATD that appears identically in both new and old files
                List<SATDInstance> untouchedSATD = oldSATDStrings.stream()
                        .filter(MappedSATDComment::isMapped)
                        .map(MappedSATDComment::getComment)
                        .map(comment -> new SATDInstance(oldKey, newKey, comment))
                        .collect(Collectors.toList());
                // SATD that was not in the new file, but was in the old
                List<SATDInstance> changedOrRemovedSATD = oldSATDStrings.stream()
                        .filter(mc -> !mc.isMapped())
                        .map(MappedSATDComment::getComment)
                        .map(comment -> new SATDInstance(oldKey, "Unknown", comment))
                        .collect(Collectors.toList());
                // SATD that was not in the old file, but was in the new
                List<SATDInstance> changedOrAddedSATD = newSATDStrings.stream()
                        .filter(mc -> !mc.isMapped())
                        .map(MappedSATDComment::getComment)
                        .map(comment -> new SATDInstance("Unknown", newKey, comment))
                        .collect(Collectors.toList());

                difference.addUnaddressedSATD(untouchedSATD);
                difference.addAddressedOrChangedSATD(changedOrRemovedSATD);
                difference.addChangedOrAddedSATD(changedOrAddedSATD);
            }
        }

        // Get commits for File Removed SATD
        difference.getFileRemovedSATD().forEach( satd ->
                getCommitsForSATD(satd, new FileRemovedOrRenamedCommitLocator(), true,
                        this.commit, newerRepository.commit));

        difference.getAddressedOrChangedSATD().forEach( satd ->
                getCommitsForSATD(satd, new SATDRemovedChangedMovedCommitLocator(), true,
                        this.commit, newerRepository.commit));

        difference.getUnaddressedSATD().forEach( satd ->
                getCommitsForSATD(satd, new SATDUnaddressedCommitLocator(), true,
                        this.commit, newerRepository.commit));

        difference.getChangedOrAddedSATD().forEach( satd ->
                getCommitsForSATD(satd, new SATDAddedCommitLocator(), false,
                        this.commit, newerRepository.commit));

        return difference;
    }

    private void getCommitsForSATD(SATDInstance satd, CommitLocator locator, boolean useOldFilePath, String startCommit, String endCommit) {
        satd.setCommitAdded(
                locator.findCommitIntroduced(this.gitInstance, satd,
                        useOldFilePath ? startCommit : endCommit,
                        useOldFilePath ? satd.getOldFile() : satd.getNewFile()));
        satd.setCommitRemoved(
                locator.findCommitAddressed(this.gitInstance, satd, startCommit, endCommit));
    }

    private Map<String, List<String>> getFilesToSAIDOccurrences(SATDDetector detector){

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

    private class MappedSATDComment {

        private String comment;
        private boolean isMapped = false;

        private MappedSATDComment(String oldComment) {
            this.comment = oldComment;
        }

        private String getComment() {
            return this.comment;
        }

        private boolean isMapped() {
            return this.isMapped;
        }

        private void setMapped() {
            this.isMapped = true;
        }
    }
}
