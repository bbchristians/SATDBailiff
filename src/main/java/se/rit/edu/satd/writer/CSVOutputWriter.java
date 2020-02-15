package se.rit.edu.satd.writer;

import com.opencsv.CSVWriter;
import se.rit.edu.satd.SATDDifference;

import java.io.*;

public class CSVOutputWriter implements OutputWriter {

    private static final String[] HEADERS = new String[]{
            "project",
            "v1_tag",
            "v2_tag",
            "commit_added",
            "commit_addressed",
            "v1_file",
            "v2_file",
            "file_when_addressed",
            "resolution",
            "satd",
            "updated_satd"
    };

    private File outFile;
    private boolean isFirstWriteToFile = true;

    public CSVOutputWriter(File outFile) {
        this.outFile = outFile;
    }

    @Override
    public void writeDiff(SATDDifference diff) throws IOException {

        if( this.isFirstWriteToFile ) {
            this.firstWrite();
            this.isFirstWriteToFile = false;
        }

        Writer writer = new BufferedWriter(new FileWriter(this.outFile, true));
        CSVWriter csvWriter = new CSVWriter(writer);

        csvWriter.writeAll(diff.toCSV());

        csvWriter.close();
    }

    private void firstWrite() throws IOException {
        // Don't append so it overwrites whatever is in the current file
        Writer writer = new BufferedWriter(new FileWriter(this.outFile, false));
        CSVWriter headerWriter = new CSVWriter(writer);

        headerWriter.writeNext(HEADERS);

        headerWriter.close();
    }
}
