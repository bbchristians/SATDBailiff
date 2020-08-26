package edu.rit.se.satd.api.modelType.request;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Satd {

    @SerializedName("commenttext")
    @Expose
    private String commentText;
    @SerializedName("id")
    @Expose
    private String id;

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
