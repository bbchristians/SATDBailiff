package edu.rit.se.satd.comment;

import com.github.javaparser.Position;
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
    final private Position start;
    @Getter
    final private Position end;
    @Getter
    final private String comment;
    @Getter
    final private String commentType;
    @Getter
    final private String containingClass;
    @Getter
    final private Position containingClassDeclarationStart;
    @Getter
    final private Position containingClassDeclarationEnd;
    @Getter
    final private String containingMethod;
    @Getter
    final private Position containingMethodDeclarationStart;
    @Getter
    final private Position containingMethodDeclarationEnd;

    public static final String TYPE_COMMENTED_SOURCE = "CommentedSource";
    public static final String TYPE_BLOCK = "Block";
    public static final String TYPE_LINE = "Line";
    public static final String TYPE_ORPHAN = "Orphan";
    public static final String TYPE_JAVADOC = "JavaDoc";
    public static final String TYPE_UNKNOWN = "Unknown";

    public GroupedComment(Comment oldComment) {
        if( !oldComment.getRange().isPresent() ) {
            System.err.println("Comment line numbers could not be found.");
            this.start = NullGroupedComment.NULL_POSITION_FIELD;
            this.end = NullGroupedComment.NULL_POSITION_FIELD;
        } else {
            this.start = oldComment.getRange().get().begin;
            this.end = oldComment.getRange().get().end;
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
                .filter(dec -> JavaParseUtil.isRangeBetweenBounds(dec.getRange().get(), this.start.line, this.end.line))
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
            this.containingClassDeclarationStart = classRange.begin;
            this.containingClassDeclarationEnd = classRange.end;

            // Method Data
            final Optional<MethodDeclaration> thisMethod = classRoot.get().findAll(MethodDeclaration.class).stream()
                    .filter(dec -> dec.getRange().isPresent())
                    .filter(dec -> JavaParseUtil.isRangeBetweenBounds(dec.getRange().get(), this.start.line, this.end.line))
                    .findFirst();
            this.containingMethod = thisMethod
                    .map(asd -> asd.getDeclarationAsString(false, false, false))
                    .orElse(UNKNOWN);
            final Range methodRange = thisMethod
                    .map(Node::getRange)
                    .orElse(Optional.of(NULL_RANGE))
                    .orElse(NULL_RANGE);
            this.containingMethodDeclarationStart = methodRange.begin;
            this.containingMethodDeclarationEnd = methodRange.end;
        } else {
            this.containingMethod = UNKNOWN;
            this.containingMethodDeclarationStart = NullGroupedComment.NULL_POSITION_FIELD;
            this.containingMethodDeclarationEnd = NullGroupedComment.NULL_POSITION_FIELD;
            this.containingClass = UNKNOWN;
            this.containingClassDeclarationStart = NullGroupedComment.NULL_POSITION_FIELD;
            this.containingClassDeclarationEnd = NullGroupedComment.NULL_POSITION_FIELD;
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
                this.start.isBefore(other.start) ? this.start : other.start,
                this.end.isAfter(other.end) ? this.end : other.end,
                this.end.isBefore(other.start) ?
                        String.join("\n", this.comment, other.comment) :
                        String.join("\n", other.comment, this.comment),
                this.commentType,
                this.containingClass,
                this.containingClassDeclarationStart,
                this.containingClassDeclarationEnd,
                this.containingMethod,
                this.containingMethodDeclarationStart,
                this.containingMethodDeclarationEnd);
    }

    /**
     * @param other another comment
     * @return True if this comment is the same type of comment, and appears directly before
     * the other comment. If this comment is a Commented source comment, then typing of the next comment is ignored
     */
    public boolean precedesDirectly(GroupedComment other) {
        return this.containingClass.equals(other.containingClass) &&
                (this.commentType.equals(other.commentType) || this.commentType.equals(TYPE_COMMENTED_SOURCE))  &&
                this.end.line + 1 == other.start.line;
    }

    public int numLines() {
        return 1 + this.end.line - this.start.line;
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
            if( this.start.isAfter(((GroupedComment) o).end) ) {
                return 1;
            } else if( this.end.isBefore(((GroupedComment) o).start) ) {
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
                    this.start.equals(((GroupedComment) obj).start) &&
                    this.end.equals(((GroupedComment) obj).end);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return (this.comment + this.containingMethod + this.containingClass + this.commentType).hashCode();
    }
}
