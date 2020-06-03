package edu.rit.se.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.comments.CommentsCollection;
import edu.rit.se.satd.comment.IgnorableWords;
import edu.rit.se.satd.comment.model.GroupedComment;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static edu.rit.se.satd.comment.model.GroupedComment.TYPE_COMMENTED_SOURCE;

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
        final Iterator<GroupedComment> allComments = parsedFile.getCommentsCollection().isPresent() ?
                ((CommentsCollection)parsedFile.getCommentsCollection().get())
                        .getComments()
                        .stream()
                        .filter(comment -> !comment.isJavadocComment())
                        .map(GroupedComment::fromJavaParserComment)
                        .filter(comment -> !comment.getCommentType().equals(TYPE_COMMENTED_SOURCE))
                        .filter(comment -> IgnorableWords.getIgnorableWords().stream()
                                .noneMatch(word -> comment.getComment().contains(word)))
                        .sorted()
                        .iterator()
                : Collections.emptyIterator();

        final List<GroupedComment> groupedComments = new ArrayList<>();
        GroupedComment previousComment = null;
        while( allComments.hasNext() ) {

            final GroupedComment thisComment = allComments.next();

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
     * @return True if the ranges overlap, else False
     */
    public static boolean isRangeBetweenBounds(Range range, int start, int end) {
        return Math.max(range.begin.line, start) <= Math.min(range.end.line, end);
    }

}
