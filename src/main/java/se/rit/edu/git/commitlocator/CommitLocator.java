package se.rit.edu.git.commitlocator;

import com.github.javaparser.JavaParser;
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

    public String findCommitIntroduced(Git gitInstance, SATDInstance satdInstance, String v1, String v2) {
        try {
            BlameCommand blameCommand = gitInstance.blame()
                    .setFilePath(satdInstance.getOldFile())
                    .setStartCommit(gitInstance.getRepository().resolve(v1));
            BlameResult blameResult = blameCommand.call();
            // Parse file as Java
            JavaParser parser = new JavaParser();
            Optional<CommentsCollection> comments = parser.parse(
                    IntStream.range(0, blameResult.getResultContents().size())
                            .mapToObj(i -> blameResult.getResultContents().getString(i))
                            .collect(Collectors.joining("\n")))
                    .getCommentsCollection();
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

    public abstract String findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2);
}
