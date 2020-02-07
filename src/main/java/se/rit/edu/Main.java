package se.rit.edu;

import com.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import se.rit.edu.git.RepositoryInitializer;
import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.satd.SATDDifference;
import satd_detector.core.utils.SATDDetector;
import se.rit.edu.satd.SATDMiner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Scanner;


public class Main {

    private static final String reposFile = "repos.txt";
    private static final String OUT_DIR = "out";

    public static void main(String[] args) throws Exception {

        File inFile = new File(reposFile);
        Scanner inFileReader = new Scanner(inFile);

        File outDir = new File(OUT_DIR);
        FileUtils.deleteDirectory(outDir);
        outDir.mkdirs();

        while( inFileReader.hasNext() ) {

            String repo = inFileReader.next();

            List<RepositoryCommitReference> repos = new SATDMiner(repo)
                    .getTaggedCommits(10);

            SATDDetector detector = new SATDDetector();
            for (int i = 1; i < repos.size(); i++) {
                SATDDifference diff = repos.get(i-1).diffAgainstNewerRepository(repos.get(i), detector);
                File outFile = new File(OUT_DIR + "/" + diff.getProjectName() + ".csv");
                BufferedWriter writer = new BufferedWriter(new FileWriter(outFile, true));
                CSVWriter csvWriter = new CSVWriter(writer);

                csvWriter.writeAll(diff.toCSV());

                csvWriter.close();
            }
        }
    }
}
