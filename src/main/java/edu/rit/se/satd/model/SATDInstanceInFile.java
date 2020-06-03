package edu.rit.se.satd.model;

import edu.rit.se.satd.comment.model.GroupedComment;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SATDInstanceInFile {

    @Getter
    @NonNull
    private String fileName;

    @Getter
    @NonNull
    private GroupedComment comment;

    @Override
    public int hashCode() {
        return (this.fileName + this.comment.getComment() +
                this.comment.getCommentType() + this.comment.getContainingClass() +
                this.comment.getContainingMethod()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if( obj instanceof  SATDInstanceInFile ) {
            return this.hashCode() == obj.hashCode();
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s \"%s\"",
                this.fileName.length() > 25 ?
                    "..." + this.fileName.substring(this.fileName.length() - 25) :
                    this.fileName,
                this.comment.getContainingClass(),
                this.comment.getContainingMethod(),
                this.comment.getComment());
    }
}
