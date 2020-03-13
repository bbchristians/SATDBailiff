package edu.rit.se.satd.comment;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import edu.rit.se.util.JavaParseUtil;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PACKAGE) // For inheritance
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For internal use only
public class GroupedComment implements Comparable {

    private static final String UNKNOWN = "None";

    @Getter
    private int startLine;
    @Getter
    private int endLine;
    @Getter
    private String comment;
    @Getter
    private String commentType;
    @Getter
    private String containingClass = UNKNOWN;
    @Getter
    private String containingMethod = UNKNOWN;

    public GroupedComment(Comment oldComment) {
        if( !oldComment.getRange().isPresent() ) {
            System.err.println("Comment line numbers could not be found.");
        } else {
            this.startLine = oldComment.getRange().get().begin.line;
            this.endLine = oldComment.getRange().get().end.line;
        }
        // Clean up comment
        this.comment = Arrays.stream(oldComment.getContent().trim().split("\n"))
                .map(GroupedComment::cleanCommentLine)
                .collect(Collectors.joining("\n"));
        this.commentType = oldComment.isBlockComment() ? "Block"
                : oldComment.isLineComment() ? "Line"
                : oldComment.isOrphan() ? "Orphan"
                : oldComment.isJavadocComment() ? "JavaDoc"
                : "Unknown";

        // Get containing class and method data if found
        final Optional<ClassOrInterfaceDeclaration> classRoot = oldComment.findRootNode().findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(dec -> dec.getRange().isPresent())
                .filter(dec -> JavaParseUtil.isRangeBetweenBounds(dec.getRange().get(), this.startLine, this.endLine))
                .findFirst();
        if( classRoot.isPresent() ) {
            this.containingClass = classRoot
                    .map(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                    .map(opt -> opt.orElse(UNKNOWN))
                    .get();
            this.containingMethod = classRoot.get().findAll(MethodDeclaration.class).stream()
                    .filter(dec -> dec.getRange().isPresent())
                    .filter(dec -> JavaParseUtil.isRangeBetweenBounds(dec.getRange().get(), this.startLine, this.endLine))
                    .map(MethodDeclaration::getNameAsString)
                    .findFirst()
                    .orElse(UNKNOWN);
        }
    }

    /**
     * Merges this comment with another comment
     * @param other another comment
     * @return a new GroupedComment instance that contains the combined
     * comments
     */
    public GroupedComment joinWith(GroupedComment other) {
        return new GroupedComment(
                Integer.min(this.startLine, other.startLine),
                Integer.max(this.endLine, other.endLine),
                this.startLine < other.startLine ?
                        String.join("\n", this.comment, other.comment) :
                        String.join("\n", other.comment, this.comment),
                this.commentType,
                this.containingClass,
                this.containingMethod);
    }

    /**
     * @param other another comment
     * @return True if this comment is the same time of comment, and appears directly before
     * the other comment
     */
    public boolean precedesDirectly(GroupedComment other) {
        return this.containingClass.equals(other.containingClass) &&
                this.commentType.equals(other.commentType) &&
                this.endLine + 1 == other.startLine;
    }

    /**
     * Removes Java-syntax items from comment lines
     * @param commentLine a line of a java comment
     * @return A cleaned line
     */
    private static String cleanCommentLine(String commentLine) {
        final String newComment = commentLine.trim();
        if( newComment.startsWith("*") ) {
            return newComment.replaceFirst("\\*", "").trim();
        }
        return newComment;
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

    @Override
    public boolean equals(Object obj) {
        if( obj instanceof  GroupedComment ) {
            return this.comment.equals(((GroupedComment) obj).getComment()) &&
                    this.startLine == ((GroupedComment) obj).startLine &&
                    this.endLine == ((GroupedComment) obj).endLine;
        }

        return super.equals(obj);
    }
}
