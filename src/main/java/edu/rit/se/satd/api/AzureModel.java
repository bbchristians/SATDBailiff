package edu.rit.se.satd.api;

import com.google.gson.Gson;
import edu.rit.se.satd.api.modelType.request.Satd;
import edu.rit.se.satd.api.modelType.request.Inputs;
import edu.rit.se.satd.api.modelType.request.SatdTypeRequest;
import edu.rit.se.satd.api.modelType.response.SatdType;
import edu.rit.se.satd.writer.OutputWriter;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class AzureModel {
    /**
     * CSV Format: API_KEY, API_URL
     * {VALUE},{VALUE}
     */
    public static String apiCredentialsFileName = "my_api_keys.csv";
    public static String apikey = null;
    public static String apiUrl = null;

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
    public static String azurePredictType( String satdBody) {
        HttpPost post;
        HttpClient client;
        StringEntity entity;
        try {
            // create HttpPost and HttpClient object
            post = new HttpPost(apiUrl);
            client = HttpClientBuilder.create().build();

            // setup output message by copying JSON body into
            // apache StringEntity object along with content type
            entity = new StringEntity(satdBody);
            entity.setContentType("text/json");

            // add HTTP headers
            post.setHeader("Accept", "text/json");
            post.setHeader("Accept-Charset", "UTF-8");

            // set Authorization header based on the API key
            post.setHeader("Authorization", ("Bearer " + apikey));
            post.setEntity(entity);

            // Call AZURE ML API and retrieve response content
            HttpResponse authResponse = client.execute(post);
            return EntityUtils.toString(authResponse.getEntity());
        }
        catch (Exception e) {
            return e.toString();
        }
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
