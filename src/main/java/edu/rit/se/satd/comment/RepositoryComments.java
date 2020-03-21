package edu.rit.se.satd.comment;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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
