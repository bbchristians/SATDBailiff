package edu.rit.se;

import edu.rit.se.satd.SATDMiner;
import edu.rit.se.satd.detector.SATDDetectorImpl;
import edu.rit.se.satd.writer.MySQLOutputWriter;

import java.io.File;
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

            final String repo = inFileReader.next();

            SATDMiner miner = new SATDMiner(repo, new SATDDetectorImpl());

            miner.writeRepoSATD(miner.getBaseCommit(null),
                    new MySQLOutputWriter("mySQL.properties"));

            miner.cleanRepo();
        }
    }
}
