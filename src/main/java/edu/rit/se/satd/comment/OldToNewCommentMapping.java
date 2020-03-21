package edu.rit.se.satd.comment;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OldToNewCommentMapping {

    @Getter
    final private GroupedComment comment;
    @Getter
    final private String file;

    @Getter
    private boolean isMapped = false;

    public void mapTo(OldToNewCommentMapping other) {
        this.isMapped = true;
        if (other != null) {
            other.isMapped = true;
        }
    }

    public boolean isNotMapped() {
        return !this.isMapped;
    }

    public boolean commentsMatch(OldToNewCommentMapping other) {
        return this.comment.getComment().equals(other.comment.getComment())
                && this.comment.getContainingMethod().equals(other.comment.getContainingMethod())
                && this.comment.getContainingClass().equals(other.comment.getContainingClass())
                && this.file.equals(other.file);
    }

}
