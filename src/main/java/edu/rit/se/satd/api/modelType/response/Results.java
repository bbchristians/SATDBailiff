package edu.rit.se.satd.api.modelType.response;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Results {
    @SerializedName("Results")
    @Expose
    private List<TypePrediction> typePrediction = null;

    public List<TypePrediction> getTypePrediction() {
        return typePrediction;
    }
    public void setTypePrediction(List<TypePrediction> typePrediction) {
        this.typePrediction = typePrediction;
    }
}
