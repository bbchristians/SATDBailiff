package edu.rit.se.satd.comment;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import edu.rit.se.util.JavaParseUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
    final private int containingClassDeclarationLineStart;
    @Getter
    final private int containingClassDeclarationLineEnd;
    @Getter
    final private String containingMethod;
    @Getter
    final private int containingMethodDeclarationLineStart;
    @Getter
    final private int containingMethodDeclarationLineEnd;

    public static final String TYPE_COMMENTED_SOURCE = "CommentedSource";
    public static final String TYPE_BLOCK = "Block";
    public static final String TYPE_LINE = "Line";
    public static final String TYPE_ORPHAN = "Orphan";
    public static final String TYPE_JAVADOC = "JavaDoc";
    public static final String TYPE_UNKNOWN = "Unknown";

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
        this.commentType = this.comment.contains("{") || this.comment.contains(";") ? TYPE_COMMENTED_SOURCE
                : oldComment.isBlockComment() ? TYPE_BLOCK
                : oldComment.isLineComment() ? TYPE_LINE
                : oldComment.isOrphan() ? TYPE_ORPHAN
                : oldComment.isJavadocComment() ? TYPE_JAVADOC
                : TYPE_UNKNOWN;

        // Get containing class and method data if found
        final Optional<ClassOrInterfaceDeclaration> classRoot = oldComment.findRootNode().findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(dec -> dec.getRange().isPresent())
                .filter(dec -> JavaParseUtil.isRangeBetweenBounds(dec.getRange().get(), this.startLine, this.endLine))
                .findFirst();
        if( classRoot.isPresent() ) {
            // Class Data
            this.containingClass = classRoot
                    .map(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                    .map(opt -> opt.orElse(UNKNOWN))
                    .get();
            final Range classRange = classRoot
                    .map(ClassOrInterfaceDeclaration::getName)
                    .map(Node::getRange)
                    .get()
                    .orElse(NULL_RANGE);
            this.containingClassDeclarationLineStart = classRange.begin.line;
            this.containingClassDeclarationLineEnd = classRange.end.line;

            // Method Data
            final Optional<MethodDeclaration> thisMethod = classRoot.get().findAll(MethodDeclaration.class).stream()
                    .filter(dec -> dec.getRange().isPresent())
                    .filter(dec -> JavaParseUtil.isRangeBetweenBounds(dec.getRange().get(), this.startLine, this.endLine))
                    .findFirst();
            this.containingMethod = thisMethod
                    .map(asd -> asd.getDeclarationAsString(false, false, false))
                    .orElse(UNKNOWN);
            final Range methodRange = thisMethod
                    .map(Node::getRange)
                    .orElse(Optional.of(NULL_RANGE))
                    .orElse(NULL_RANGE);
            this.containingMethodDeclarationLineStart = methodRange.begin.line;
            this.containingMethodDeclarationLineEnd = methodRange.end.line;
        } else {
            this.containingMethod = UNKNOWN;
            this.containingMethodDeclarationLineStart = -1;
            this.containingMethodDeclarationLineEnd = -1;
            this.containingClass = UNKNOWN;
            this.containingClassDeclarationLineStart = -1;
            this.containingClassDeclarationLineEnd = -1;
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
                this.containingClassDeclarationLineStart,
                this.containingClassDeclarationLineEnd,
                this.containingMethod,
                this.containingMethodDeclarationLineStart,
                this.containingMethodDeclarationLineEnd);
    }

    /**
     * @param other another comment
     * @return True if this comment is the same type of comment, and appears directly before
     * the other comment. If this comment is a Commented source comment, then typing of the next comment is ignored
     */
    public boolean precedesDirectly(GroupedComment other) {
        return this.containingClass.equals(other.containingClass) &&
                (this.commentType.equals(other.commentType) || this.commentType.equals(TYPE_COMMENTED_SOURCE))  &&
                this.endLine + 1 == other.startLine;
    }

    public int numLines() {
        return 1 + this.endLine - this.startLine;
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
