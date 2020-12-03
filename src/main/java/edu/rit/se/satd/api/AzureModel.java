package edu.rit.se.satd.api;

import com.google.gson.Gson;
import edu.rit.se.satd.api.modelType.request.Inputs;
import edu.rit.se.satd.api.modelType.request.Satd;
import edu.rit.se.satd.api.modelType.request.SatdTypeRequest;
import edu.rit.se.satd.api.modelType.response.SatdType;
import edu.rit.se.satd.writer.OutputWriter;
import org.apache.http.client.fluent.*;
import org.apache.http.entity.ContentType;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

public class AzureModel {
    /**
     * CSV Format: API_KEY, API_URL
     * {VALUE},{VALUE}
     */
    public static String apiCredentialsFileName = "my_api_keys.csv";
    public static String apikey = null;
    public static String apiUrl = null;
    private static final Gson gson = new Gson();
    /**
     * Classifies removed satd comments in a repo
     * @param writer
     * @param projectURL
     */
    public static void classiffySATD (OutputWriter writer, String projectURL){
        try {
        //Read API credentials from file - Note: csv file with api credentials must be created when first cloning the repo
        BufferedReader br = new BufferedReader(new FileReader(apiCredentialsFileName));
        //Labels
        br.readLine();
        //Actual credential values
        String[] credentials = br.readLine().split(",");
        apikey = credentials[0];
        apiUrl = credentials[1];

        URL aURL = new URL(projectURL);
        String projectName = aURL.getPath().replaceFirst("\\/", "");
        System.out.println("--- Classifying removed SATD for " + projectName + " ---");

        //Get removed satd comments from project
        System.out.println("Locating removed satd ...");
        Map<String,String> satdMap = writer.getRemovedSATD(projectName, projectURL);

        //Create request object
        Gson gson = new Gson();
        SatdTypeRequest requestObj = createRequestObject(satdMap);
        String json = gson.toJson(requestObj);

        //Call AZURE web service to make classification
        System.out.println("Classification of removed satd ...");
        String answer = azurePredictType(json);

        //Parse answer
        SatdType results= gson.fromJson(answer, SatdType.class);
        Map<String, String> predictions = results.getResults();

        //Write results to db
        System.out.println("Saving results ...");
        writer.writePredictionResults(predictions);
        }catch(IOException e){
            System.out.println(e);
        } catch (SQLException e){
            System.out.println(e);
        }
    }

    /**
     * Makes a Post request to the AZURE webservice in order to get classifications
     * @param satdBody - Satd comments to be classified
     * @return - Type for each comment
     */
    public static String azurePredictType(String satdBody){
        String response = "";
        try {
            // Create the request
            Content content = Request.Post(apiUrl)
                    .addHeader("Content-Type", "application/json")
                    // Only needed if using authentication
                    .addHeader("Authorization", "Bearer " + apikey)
                    // Set the JSON data as the body
                    .bodyString(satdBody, ContentType.APPLICATION_JSON)
                    // Make the request and display the response.
                    .execute().returnContent();
            response = content.toString();
        }
        catch (Exception e) {
            System.out.println(e);
        }
        return response;
    }

    /**
     * Creates a formatted object to be send as a body param inside the POST Request
     * If the body param is not properly formatted the request fails
     * Format: inputs: commentText,id: [element, element]
     * @param satdMap - key, value pairs - comment id and comment
     * @return - formatted object
     */
    private static SatdTypeRequest createRequestObject(Map<String,String> satdMap){
        ArrayList<Satd> satdList = new ArrayList<Satd>();

        for (String id : satdMap.keySet())
        {
            String type = satdMap.get(id);
            //List item
            Satd singleSatd = new Satd();
            singleSatd.setCommentText(type);
            singleSatd.setId(id);
            //add item to list
            satdList.add(singleSatd);
        }

        Inputs request = new Inputs();
        //Add list to the input
        request.setCommentTextId(satdList);
        SatdTypeRequest requestObj = new SatdTypeRequest();
        //add the input to the request
        requestObj.setInputs(request);
        return requestObj;
    }
}
