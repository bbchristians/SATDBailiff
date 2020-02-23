package se.rit.edu.git.commitlocator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import se.rit.edu.git.models.CommitMetaData;
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

    public void findContributingCommits(Git gitInstance, SATDInstance satdInstance, String commitToStart, String file) {
        try {
            // Make and call git blame for the specified file at the start commit
            final BlameResult blameResult = gitInstance.blame()
                    .setFilePath(file)
                    .setStartCommit(gitInstance.getRepository().resolve(commitToStart))
                    .call();
            // Get blame result
            final String blameResultJavaCode =
                    IntStream.range(0, blameResult.getResultContents().size())
                            .mapToObj(i -> blameResult.getResultContents().getString(i))
                            // Some files use different carriage returns, which construes git line numbers
                            // Due to OS-specific editors, so replace all of them with \n
                            // see: https://github.com/apache/maven-surefire/blob/93687b7c61f373ae6c9423c6e612f6901a7732ad/surefire-api/src/main/java/org/apache/maven/surefire/util/internal/ObjectUtils.java
                            .map(i -> i.replace('\r', ' '))
                            .collect(Collectors.joining("\n"));
            // Parse file as Java, and try to find the comment we're looking for
            final List<GroupedComment> soughtComment = JavaParseUtil.parseFileForComments(
                    new ByteArrayInputStream(blameResultJavaCode.getBytes())).stream()
                    .filter(comment -> comment.getComment().equals(satdInstance.getCommentOld()))
                    .collect(Collectors.toList());
            // TODO don't always blame the first occurrence -- multiple of same SATD in one file w/ diff commits
            //  Will produce an error
            if( !soughtComment.isEmpty() ) {
                final GroupedComment singleComment = soughtComment.get(0);
                IntStream.range(singleComment.getStartLine() - 1,
                        singleComment.getStartLine() + singleComment.getNumLines())
                        .mapToObj(blameResult::getSourceCommit)
                        .map(CommitMetaData::new)
                        .distinct()
                        .forEach(satdInstance::addInitialBlameCommit);
            }
        } catch (GitAPIException e) {
            System.err.println("Git API error while blaming file.");
        } catch (IOException e) {
            System.err.println("Error resolving commit hash in repository.");
        } catch (IllegalCharsetNameException e) {
            System.err.println("Illegal charset name: " + e.getCharsetName());
        }
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
    public abstract void findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2);

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
