package edu.rit.se.satd.comment.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of comments, also containing possible errors (from parsing)
 */
@NoArgsConstructor
public class RepositoryComments {

    @Getter
    private final List<GroupedComment> comments = new ArrayList<>();
    @Getter
    private final List<String> parseErrorFiles = new ArrayList<>();

    public void addParseErrorFile(String fileName) {
        this.parseErrorFiles.add(fileName);
    }

    public void addComments(List<GroupedComment> comments) {
        this.comments.addAll(comments);
    }
}
