package edu.rit.se.satd.refactoring.model;

import lombok.Getter;

public class RefactoringHistory {
    @Getter
    private String filePath;

    @Getter
    private int startLine;

    @Getter
    private int endLine;

    @Getter
    private int startColumn;

    @Getter
    private int endColumn;

    @Getter
    private String description;

    @Getter
    private String codeElement;

    public  RefactoringHistory(String filePath, int startLine, int endLine, int startColumn, int endColumn, String description, String codeElement){
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.startColumn = startColumn;
        this.endColumn = endColumn;
        this.description = description;
        this.codeElement = codeElement;
    }

    public void showMe (){
        System.out.println("--------");
        System.out.println("FilePath: " + this.filePath + " start line: " + this.startLine + " end line: " + this.endLine + " start column: " + this.startColumn + " end column: " + this.endColumn);
        System.out.println(this.description);
        System.out.println("--------");

    }
}
