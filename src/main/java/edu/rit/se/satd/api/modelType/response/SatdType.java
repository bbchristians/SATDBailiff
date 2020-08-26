package edu.rit.se.satd.api.modelType.response;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
public class SatdType {

    @SerializedName("Results")
    @Expose
    private Results results;

    public HashMap<String, String> getResults() {
        HashMap<String, String> predictions = new HashMap<String, String>();
        for(TypePrediction prediction : results.getTypePrediction()){
            predictions.put(prediction.getId(),prediction.getScoredLabels());
        }
        return predictions;
    }

    public void setResults(Results results) {
        this.results = results;
    }

}
