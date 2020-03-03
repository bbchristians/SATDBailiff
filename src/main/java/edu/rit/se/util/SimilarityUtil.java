package edu.rit.se.util;

import edu.rit.se.satd.comment.GroupedComment;
import org.apache.commons.text.similarity.LevenshteinDistance;

/**
 * Utility class to contain string similarity comparison logic
 */
public class SimilarityUtil {

    // A constant value which determines the Levenshtein distance which a comment
    // must share with another comment in order to assume one was modified to become
    // the other
    private static final double LEVENSHTEIN_DISTANCE_MIN = 0.50;

    /**
     * Determines if the two comments are similar enough to constitute them being classified
     * as one being modified into being the other, rather than one being removed and one added
     * in an un-related manner
     * @param comment1 A grouped comment
     * @param comment2 A grouped comment
     * @return True if the comments are similar enough, else false
     */
    public static boolean commentsAreSimilar(GroupedComment comment1, GroupedComment comment2) {
        if( comment1.getComment().isEmpty() && comment2.getComment().isEmpty() ) {
            return true;
        }
        return LEVENSHTEIN_DISTANCE_MIN >= new LevenshteinDistance()
                .apply(comment1.getComment(), comment2.getComment()) /
                    (double)Integer.max(comment1.getComment().length(), comment2.getComment().length());
    }
}
