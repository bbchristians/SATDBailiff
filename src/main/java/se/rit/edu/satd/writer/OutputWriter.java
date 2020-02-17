package se.rit.edu.satd.writer;

import se.rit.edu.satd.SATDDifference;

import java.io.IOException;

public interface OutputWriter {

    /**
     * Writes the SATD diff instance to an output format
     * @param diff an SATDDifference object from a comparison between two project tags
     * @throws IOException thrown if an error is encountered during processing
     */
    void writeDiff(SATDDifference diff) throws IOException;

}
