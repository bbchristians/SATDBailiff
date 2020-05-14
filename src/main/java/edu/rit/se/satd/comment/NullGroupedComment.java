package edu.rit.se.satd.comment;

public class NullGroupedComment extends GroupedComment {

    public static String NULL_FIELD = "None";
    private static int NULL_FIELD_INT = -1;

    public NullGroupedComment() {
        super(NULL_FIELD_INT, NULL_FIELD_INT, NULL_FIELD, NULL_FIELD,
                NULL_FIELD, NULL_FIELD_INT, NULL_FIELD_INT,
                NULL_FIELD, NULL_FIELD_INT, NULL_FIELD_INT);
    }
}
