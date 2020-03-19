package edu.rit.se.satd.comment;


import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OldToNewCommentMapping {

    @Getter
    @NonNull
    private GroupedComment comment;
    @Getter
    @NonNull
    private String file;

    @Getter
    private boolean isMapped = false;

    public void mapTo(OldToNewCommentMapping other) {
        this.isMapped = true;
        other.isMapped = true;
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
