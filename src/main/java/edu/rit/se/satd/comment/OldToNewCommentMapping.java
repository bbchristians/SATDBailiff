package edu.rit.se.satd.comment;


import lombok.*;

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
                && this.file.equals(other.file);
    }

}
