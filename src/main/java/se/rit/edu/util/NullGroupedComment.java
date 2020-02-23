package se.rit.edu.util;


public class NullGroupedComment extends GroupedComment {

    public NullGroupedComment() {}

    public int getStartLine() {
        return -1;
    }

    public int getEndLine() {
        return -1;
    }

    public String getComment() {
        return "None";
    }

    public int getNumLines() {
        return 0;
    }

    public String getCommentType() {
        return "None";
    }
}
