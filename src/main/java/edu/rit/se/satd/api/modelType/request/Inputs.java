package edu.rit.se.satd.api.modelType.request;

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Inputs {
    @SerializedName("commenttext, id")
    @Expose
    private List<Satd> commentTextId = null;

    public List<Satd> getCommentTextId() {
        return commentTextId;
    }

    public void setCommentTextId(List<Satd> commenttextId) {
        this.commentTextId = commenttextId;
    }
}
