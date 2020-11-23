package edu.rit.se.satd.refactoring.model;

import lombok.Getter;

public class SatdRemoval {
    @Getter
    private String leftHash;
    @Getter
    private String rightHash;
    @Getter
    private int commentId;

    public SatdRemoval(String leftHash, String rightHash,int commentId){
        this.leftHash = leftHash;
        this.rightHash = rightHash;
        this.commentId = commentId;
    }

    public String toString(){
        return this.leftHash + "," + rightHash + "," + this.commentId;
    }
}
