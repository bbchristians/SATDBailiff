package edu.rit.se.satd.comment;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class NullGroupedComment extends GroupedComment {

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
