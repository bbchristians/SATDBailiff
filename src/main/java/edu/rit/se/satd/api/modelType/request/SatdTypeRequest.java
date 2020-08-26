package edu.rit.se.satd.api.modelType.request;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SatdTypeRequest {
    @SerializedName("Inputs")
    @Expose
    private Inputs inputs;

    public Inputs getInputs() {
        return inputs;
    }

    public void setInputs(Inputs inputs) {
        this.inputs = inputs;
    }
}