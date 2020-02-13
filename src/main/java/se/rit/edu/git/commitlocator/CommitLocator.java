package se.rit.edu.git.commitlocator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.CommentsCollection;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import se.rit.edu.satd.SATDInstance;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
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
            JavaParser parser = new JavaParser();
            ParseResult parsedBlameOutput = parser.parse(blameResultJavaCode);
            Optional<CommentsCollection> comments = parsedBlameOutput.getCommentsCollection();
            if( comments.isPresent() ) {
                List<Comment> soughtComment = comments.get()
                        .getComments()
                        .stream()
                        .filter(comment -> comment.getContent().trim().equals(satdInstance.getSATDComment().trim()))
                        .collect(Collectors.toList());
                if( !soughtComment.isEmpty() &&
                        soughtComment.get(0).getRange().isPresent() ) {
                    // TODO Blame all lines of the comment in case two lines blame to different commits
                    int commentLineNumber = soughtComment.get(0).getRange().get().begin.line;
                    return blameResult.getSourceCommit(commentLineNumber).getName();
                }
                return SATDInstance.COMMIT_UNKNOWN;
            }
        } catch (GitAPIException e) {
            System.err.println("Git API error while blaming file.");
        } catch (IOException e) {
            System.err.println("Error resolving commit hash in repository.");
        }
        return null;
    }

    /**
     * Gets the commit hash of the commit which addressed the SATD
     * Also, updates the SATD with a resolution type
     * @param gitInstance a git instance
     * @param satdInstance a SATD instance
     * @param v1 the first version of the code (Commit hash)
     * @param v2 the second version of the code (Commit hash)
     * @return A commit hash (String) of the commit which addressed the SATD
     */
    public abstract String findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2);
}
