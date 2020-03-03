package se.rit.edu.satd.comment;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import org.jetbrains.annotations.NotNull;
import se.rit.edu.util.JavaParseUtil;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class GroupedComment implements Comparable {

    private static final String UNKNOWN = "None";

    private int startLine;
    private int endLine;
    private String comment;
    private String commentType;
    private String containingClass = UNKNOWN;
    private String containingMethod = UNKNOWN;

    // Default constructor for inheritance
    GroupedComment() { }

    public GroupedComment(Comment oldComment) {
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
        this.commentType = oldComment.isBlockComment() ? "Block"
                : oldComment.isLineComment() ? "Line"
                : oldComment.isOrphan() ? "Orphan"
                : oldComment.isJavadocComment() ? "JavaDoc"
                : "Unknown";
        // Get containing class and method if found
        final Optional<ClassOrInterfaceDeclaration> classRoot = oldComment.findRootNode().findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(dec -> dec.getRange().isPresent())
                .filter(dec -> JavaParseUtil.commentInRange(dec.getRange().get(), this.startLine, this.endLine))
                .findFirst();
        if( classRoot.isPresent() ) {
            this.containingClass = classRoot
                    .map(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                    .map(opt -> opt.orElse(UNKNOWN))
                    .get();
            this.containingMethod = classRoot.get().findAll(MethodDeclaration.class).stream()
                    .filter(dec -> dec.getRange().isPresent())
                    .filter(dec -> JavaParseUtil.commentInRange(dec.getRange().get(), this.startLine, this.endLine))
                    .map(MethodDeclaration::getNameAsString)
                    .findFirst()
                    .orElse(UNKNOWN);
        }
    }

    private GroupedComment(int startLine, int endLine, String comment, String commentType, String containingClass) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.comment = comment;
        this.commentType = commentType;
        this.containingClass = containingClass;
    }

    public GroupedComment joinWith(GroupedComment other) {
        return new GroupedComment(
                Integer.min(this.startLine, other.startLine),
                Integer.max(this.endLine, other.endLine),
                String.join("\n", this.comment, other.comment),
                this.commentType,
                this.containingClass);
    }

    public boolean precedesDirectly(GroupedComment other) {
        return this.containingClass.equals(other.containingClass) &&
                this.commentType.equals(other.commentType) &&
                this.endLine + 1 == other.startLine;
    }

    public String getContainingClass() {
        return this.containingClass;
    }

    public String getContainingMethod() {
        return this.containingMethod;
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

    public String getCommentType() {
        return this.commentType;
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
