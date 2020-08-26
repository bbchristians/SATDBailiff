package edu.rit.se.satd.api.modelType.response;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TypePrediction {
    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("Scored Labels")
    @Expose
    private String scoredLabels;
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getScoredLabels() {
        return scoredLabels;
    }
    public void setScoredLabels(String scoredLabels) {
        this.scoredLabels = scoredLabels;
    }
}