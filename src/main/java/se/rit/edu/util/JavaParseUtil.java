package se.rit.edu.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.comments.CommentsCollection;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class JavaParseUtil {

    public static List<GroupedComment> parseFileForComments(InputStream file) {
        final JavaParser parser = new JavaParser();
        final ParseResult parsedFile = parser.parse(file);
        final List<GroupedComment> allComments = parsedFile.getCommentsCollection().isPresent() ?
                ((CommentsCollection)parsedFile.getCommentsCollection().get())
                        .getComments()
                        .stream()
                        .filter(comment -> !comment.isJavadocComment())
                        .map(GroupedComment::new)
                        .sorted()
                        .collect(Collectors.toList())
                : new ArrayList<>();

        final List<GroupedComment> groupedComments = new ArrayList<>();
        GroupedComment previousComment = null;
        for( GroupedComment thisComment : allComments ) {
            if( previousComment != null && previousComment.precedesDirectly(thisComment) ) {
                previousComment = previousComment.joinWith(thisComment);
            } else {
                groupedComments.add(previousComment);
                previousComment = thisComment;
            }
        }
        if( !groupedComments.contains(previousComment) ) {
            groupedComments.add(previousComment);
        }
        return allComments.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
