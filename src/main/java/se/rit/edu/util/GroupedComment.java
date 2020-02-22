package se.rit.edu.util;

import com.github.javaparser.ast.comments.Comment;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public class GroupedComment implements Comparable {

    private int startLine;
    private int endLine;
    private String comment;

    GroupedComment(Comment oldComment) {
        if( !oldComment.getRange().isPresent() ) {
            System.err.println("Comment line numbers could not be found.");
        } else {
            this.startLine = oldComment.getRange().get().begin.line;
            this.endLine = oldComment.getRange().get().end.line;
        }
        // Clean up comment
        this.comment = Arrays.stream(oldComment.getContent().trim().split("\n"))
                .map(String::trim)
                .map(commentLine -> {
                    if( commentLine.startsWith("*") ) {
                        return commentLine.replaceFirst("\\*", "");
                    }
                    return commentLine;
                })
                .map(String::trim)
                .collect(Collectors.joining("\n"));

    }

    private GroupedComment(int startLine, int endLine, String comment) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.comment = comment;
    }

    public GroupedComment joinWith(GroupedComment other) {
        return new GroupedComment(
                Integer.min(this.startLine, other.startLine),
                Integer.max(this.endLine, other.endLine),
                String.join("\n", this.comment, other.comment));
    }

    public boolean precedesDirectly(GroupedComment other) {
        return this.getEndLine() + 1 == other.getStartLine();
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public String getComment() {
        return comment;
    }

    public int getNumLines() {
        return this.endLine - this.startLine + 1;
    }

    @Override
    public int compareTo(@NotNull Object o) {
        if( o instanceof GroupedComment ) {
            if( this.startLine > ((GroupedComment) o).startLine ) {
                return 1;
            } else if( this.startLine < ((GroupedComment) o).startLine ) {
                return -1;
            }
            return 0;
        }
        return -1;
    }
}
