package se.rit.edu.satd.writer;

import se.rit.edu.satd.SATDDifference;

import java.io.IOException;

public interface OutputWriter {

    void writeDiff(SATDDifference diff) throws IOException;

}
