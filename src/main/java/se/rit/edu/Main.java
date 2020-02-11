package se.rit.edu;

import com.opencsv.CSVWriter;
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

    private static final String reposFile = "repos_remaining.txt";
    private static final String OUT_DIR = "out";

    public static void main(String[] args) throws Exception {

        File inFile = new File(reposFile);
        Scanner inFileReader = new Scanner(inFile);

        File outDir = new File(OUT_DIR);
        outDir.mkdirs();

        while( inFileReader.hasNext() ) {

            String repo = inFileReader.next();

            if( !repo.endsWith(".git") ) {
                repo += ".git";
            }

            SATDMiner miner = new SATDMiner(repo);
            List<RepositoryCommitReference> repos = miner.getReposAtReleases(SATDMiner.ReleaseSortType.RELEASE_PARSE);

            System.out.println(String.format("%d tags found in repository.", repos.size()));

            SATDDetector detector = new SATDDetector();
            for (int i = 1; i < repos.size(); i++) {
                SATDDifference diff = repos.get(i-1).diffAgainstNewerRepository(repos.get(i), detector);
                File outFile = new File(OUT_DIR + "/" + diff.getProjectName() + ".csv");
                BufferedWriter writer = new BufferedWriter(new FileWriter(outFile, true));

                CSVWriter csvWriter = new CSVWriter(writer);
                if( i == 1 ) {
                    csvWriter.writeNext(new String[]{
                            "project",
                            "v1_tag",
                            "v2_tag",
                            "commit_added",
                            "commit_addressed",
                            "v1_file",
                            "v2_file",
                            "file_when_addressed",
                            "resolution",
                            "satd"
                    });
                }

                csvWriter.writeAll(diff.toCSV());

                csvWriter.close();
            }

            miner.cleanRepo();
        }
    }
}