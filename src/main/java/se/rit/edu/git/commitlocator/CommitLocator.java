package se.rit.edu.git.commitlocator;

import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import se.rit.edu.satd.SATDInstance;
import se.rit.edu.util.GroupedComment;
import se.rit.edu.util.JavaParseUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class CommitLocator {

    public String findCommitIntroduced(Git gitInstance, SATDInstance satdInstance, String commitToStart, String file) {
        try {
            BlameCommand blameCommand = gitInstance.blame()
                    .setFilePath(file)
                    .setStartCommit(gitInstance.getRepository().resolve(commitToStart));
            BlameResult blameResult = blameCommand.call();
            // Get blame result
            String blameResultJavaCode =
                    IntStream.range(0, blameResult.getResultContents().size())
                            .mapToObj(i -> blameResult.getResultContents().getString(i))
                            // Some files use different carriage returns, which construes git line numbers
                            // Due to OS-specific editors, so replace all of them with \n
                            // see: https://github.com/apache/maven-surefire/blob/93687b7c61f373ae6c9423c6e612f6901a7732ad/surefire-api/src/main/java/org/apache/maven/surefire/util/internal/ObjectUtils.java
                            .map(i -> i.replace('\r', ' '))
                            .collect(Collectors.joining("\n"));
            // Parse file as Java
            List<GroupedComment> parsedBlameOutput = JavaParseUtil.parseFileForComments(
                    new ByteArrayInputStream(blameResultJavaCode.getBytes()));
            List<GroupedComment> soughtComment = parsedBlameOutput
                    .stream()
                    .filter(comment -> comment.getComment().equals(satdInstance.getSATDComment()))
                    .collect(Collectors.toList());
            if( !soughtComment.isEmpty() ) {
                // TODO Blame all lines of the comment in case two lines blame to different commits
                int commentLineNumber = soughtComment.get(0).getStartLine();
                // Comment line is not indexed
                return blameResult.getSourceCommit(commentLineNumber - 1).getName();
            }
            return SATDInstance.COMMIT_UNKNOWN;
        } catch (GitAPIException e) {
            System.err.println("Git API error while blaming file.");
        } catch (IOException e) {
            System.err.println("Error resolving commit hash in repository.");
        } catch (IllegalCharsetNameException e) {
            System.err.println("Illegal charset name: " + e.getCharsetName());
        }
        return SATDInstance.COMMIT_UNKNOWN;
    }

    /**
     * Gets the commit hash of the commit which addressed the SATD
     * Also, updates the SATD with a resolution type, fileWhenAddressed, and v2 file if needed
     * @param gitInstance a git instance
     * @param satdInstance a SATD instance
     * @param v1 the first version of the code (Commit hash)
     * @param v2 the second version of the code (Commit hash)
     * @return A commit hash (String) of the commit which addressed the SATD
     */
    public abstract String findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2);

    static List<DiffEntry> getDiffEntries(Git gitInstance, RevTree tree1, RevTree tree2) {
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
}
