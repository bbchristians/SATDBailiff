package edu.rit.se.satd.comment;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import edu.rit.se.util.JavaParseUtil;
import lombok.*;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static edu.rit.se.util.JavaParseUtil.NULL_RANGE;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE) // For internal use only
public class GroupedComment implements Comparable {

    private static final String UNKNOWN = "None";

    @Getter
    final private int startLine;
    @Getter
    final private int endLine;
    @Getter
    final private String comment;
    @Getter
    final private String commentType;
    @Getter
    final private String containingClass;
    @Getter
    final private int containingClassDeclarationLine;
    @Getter
    final private String containingMethod;
    @Getter
    final private int containingMethodDeclarationLine;

    public GroupedComment(Comment oldComment) {
        if( !oldComment.getRange().isPresent() ) {
            System.err.println("Comment line numbers could not be found.");
            this.startLine = -1;
            this.endLine = -1;
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
            this.containingClassDeclarationLine = classRoot
                    .map(ClassOrInterfaceDeclaration::getName)
                    .map(Node::getRange)
                    .get()
                    .orElse(new Range(new Position(-1, -1), new Position(-1, -1)))
                    .begin.line;
            final Optional<MethodDeclaration> thisMethod = classRoot.get().findAll(MethodDeclaration.class).stream()
                    .filter(dec -> dec.getRange().isPresent())
                    .filter(dec -> JavaParseUtil.isRangeBetweenBounds(dec.getRange().get(), this.startLine, this.endLine))
                    .findFirst();
            this.containingMethod = thisMethod
                    .map(MethodDeclaration::getNameAsString)
                    .orElse(UNKNOWN);
            this.containingMethodDeclarationLine = thisMethod
                    .map(Node::getRange)
                    .orElse(Optional.of(NULL_RANGE))
                    .orElse(NULL_RANGE)
                    .begin.line;
        } else {
            this.containingMethod = UNKNOWN;
            this.containingMethodDeclarationLine = -1;
            this.containingClass = UNKNOWN;
            this.containingClassDeclarationLine = -1;
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
                this.containingClassDeclarationLine,
                this.containingMethod,
                this.containingClassDeclarationLine);
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
    public int compareTo(Object o) {
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

    @Override
    public int hashCode() {
        return (this.comment + this.containingMethod + this.containingClass + this.commentType).hashCode();
    }
}
