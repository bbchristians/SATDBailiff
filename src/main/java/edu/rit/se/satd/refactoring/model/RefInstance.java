package edu.rit.se.satd.refactoring.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

public class RefInstance {
    @Setter
    @Getter
    private int projectID;
    @Getter
    private String commitID;
    @Getter
    private String refactoringType;
    @Getter
    private String refactoringDescription;
    @Getter
    private int commentID;

    @Getter
    @Setter
    private int refactoringID;

    @Getter
    @Setter
    private ArrayList<RefactoringHistory> refactoringsBefore;

    @Getter
    @Setter
    private ArrayList<RefactoringHistory>  refactoringsAfter;

    public RefInstance( String commitID, String refactoringType, String refactoringDescription){
        this.commitID = commitID;
        this.refactoringType = refactoringType;
        this.refactoringDescription = refactoringDescription;
    }
}
