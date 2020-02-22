package se.rit.edu.satd.writer;

import com.opencsv.CSVWriter;
import se.rit.edu.satd.SATDDifference;
import se.rit.edu.satd.SATDInstance;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CSVOutputWriter implements OutputWriter {

    // Headers for the CSV file
    private static final String[] HEADERS = new String[]{
            "project",
            "v1_tag",
            "v2_tag",
            "commit_added",
            "commit_addressed",
            "v1_file",
            "start_line",
            "end_line",
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
            this.outFile.getParentFile().mkdirs();
            this.firstWrite();
            this.isFirstWriteToFile = false;
        }

        Writer writer = new BufferedWriter(new FileWriter(this.outFile, true));
        CSVWriter csvWriter = new CSVWriter(writer);

        csvWriter.writeAll(diffToCSV(diff));

        csvWriter.close();
    }

    private void firstWrite() throws IOException {
        // Don't append so it overwrites whatever is in the current file
        Writer writer = new BufferedWriter(new FileWriter(this.outFile, false));
        CSVWriter headerWriter = new CSVWriter(writer);

        headerWriter.writeNext(HEADERS);

        headerWriter.close();
    }

    private static List<String[]> diffToCSV(SATDDifference diff) {
        List<String[]> allCSVEntries = new ArrayList<>();
        allCSVEntries.addAll(instanceListToCSV(diff, diff.getFileRemovedSATD()));
        allCSVEntries.addAll(instanceListToCSV(diff, diff.getUnaddressedSATD()));
        allCSVEntries.addAll(instanceListToCSV(diff, diff.getAddressedOrChangedSATD()));
        allCSVEntries.addAll(instanceListToCSV(diff, diff.getChangedOrAddedSATD()));
        return allCSVEntries;
    }

    private static List<String[]> instanceListToCSV(SATDDifference diff, List<SATDInstance> list) {
        return list.stream()
                .map(CSVOutputWriter::instanceToCSV)
                .map(csv -> new String[] {
                        diff.getProjectName(),
                        diff.getOldTag(),
                        diff.getNewTag(),
                        csv[0],
                        csv[1],
                        csv[2],
                        csv[3],
                        csv[4],
                        csv[5],
                        csv[6],
                        csv[7],
                        csv[8],
                        csv[9]
                })
                .collect(Collectors.toList());
    }

    private static String[] instanceToCSV(SATDInstance satdInstance) {
        return new String[] {
                satdInstance.getContributingCommits().isEmpty() ?
                        SATDInstance.COMMIT_UNKNOWN
                        : String.join(", ", satdInstance.getContributingCommits()),
                satdInstance.getCommitRemoved(),
                satdInstance.getOldFile(),
                "" + satdInstance.getStartLineNumberOldFile(),
                "" + satdInstance.getEndLineNumberOldFile(),
                satdInstance.getNewFile(),
                satdInstance.getNameOfFileWhenAddressed(),
                satdInstance.getResolution().name(),
                satdInstance.getSATDComment(),
                satdInstance.getCommentChangedTo()};
    }
}
