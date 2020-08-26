package edu.rit.se.satd.writer;

import edu.rit.se.satd.model.SATDDifference;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

public interface OutputWriter {

    /**
     * Writes the SATD diff instance to an output format
     * @param diff an SATDDifference object from a comparison between two project tags
     * @throws IOException thrown if an error is encountered during processing
     */
    void writeDiff(SATDDifference diff) throws IOException;

    /**
     * Finishes any write processes and terminated the writer
     */
    void close();

    /**
     * Gets comments of removed satd
     * @param projectName - The name of the project
     * @param projectURL -  The url of the project
     * @return - key, value pair list of comments,ids
     * @throws SQLException
     */
    Map<String,String> getRemovedSATD(String projectName, String projectURL) throws SQLException;;

    /**
     * Saves Azure classifications in the db
     * @param predictions - list of key value pairs comments, ids
     * @throws SQLException
     */
    void writePredictionResults(Map<String, String> predictions ) throws SQLException;
}
