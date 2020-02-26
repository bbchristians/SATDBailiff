package se.rit.edu.util;


public class NullGroupedComment extends GroupedComment {

    public NullGroupedComment() {}

    @Override
    public int getStartLine() {
        return -1;
    }

    @Override
    public int getEndLine() {
        return -1;
    }

    @Override
    public String getComment() {
        return "None";
    }

    @Override
    public int getNumLines() {
        return 0;
    }

    @Override
    public String getCommentType() {
        return "None";
    }

    @Override
    public String getContainingClass() {
        return "None";
    }

    @Override
    public String getContainingMethod() {
        return "None";
    }
}
