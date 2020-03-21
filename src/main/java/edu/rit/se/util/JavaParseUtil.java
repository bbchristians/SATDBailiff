package edu.rit.se.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.comments.CommentsCollection;
import edu.rit.se.satd.comment.GroupedComment;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JavaParseUtil {

    public static Range NULL_RANGE = new Range(new Position(-1, -1), new Position(-1, -1));

    /**
     * Gets a list of comments from the input java file
     * @param file An input stream containing the contents of a java file to parse for comments
     * @return a list of grouped comments that correlate to comments from the parsed java file
     */
    public static List<GroupedComment> parseFileForComments(InputStream file, String fileName) throws KnownParserException {
        final JavaParser parser = new JavaParser();
        final ParseResult parsedFile = parser.parse(file);
        if( !parsedFile.getProblems().isEmpty() ) {
            throw new KnownParserException(fileName);
        }
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
                // Previous comment was the last of the group, so add it to the list
                if( previousComment != null ) {
                    groupedComments.add(previousComment);
                }
                // restart grouping with the current comment
                previousComment = thisComment;
            }
        }
        if( previousComment != null && !groupedComments.contains(previousComment) ) {
            groupedComments.add(previousComment);
        }
        return groupedComments;
    }

    /**
     * Determines if the given range occurred within the start and end bounds
     * @param range The range of the edit
     * @param start the starting bound
     * @param end the ending bound
     * @return True if the range overlaps with the start and end boundaries, else False
     */
    public static boolean isRangeBetweenBounds(Range range, int start, int end) {
        return
                // Starts before the start and ends after the start
                (range.begin.line <= start && range.end.line >= start ) ||
                        // Starts before the end, and ends after the end
                        (range.begin.line <= end && range.end.line >= end) ||
                        // Starts after the start and ends before the end
                        (range.begin.line >= start && range.end.line <= end);
    }

}
