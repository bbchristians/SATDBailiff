package edu.rit.se.satd.comment;

import com.github.javaparser.Position;

public class NullGroupedComment extends GroupedComment {

    public static String NULL_FIELD = "None";
    public static Position NULL_POSITION_FIELD = new Position(-1, -1);

    public NullGroupedComment() {
        super(NULL_POSITION_FIELD, NULL_POSITION_FIELD, NULL_FIELD, NULL_FIELD,
                NULL_FIELD, NULL_POSITION_FIELD, NULL_POSITION_FIELD,
                NULL_FIELD, NULL_POSITION_FIELD, NULL_POSITION_FIELD);
    }
}
