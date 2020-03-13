package edu.rit.se;

import edu.rit.se.git.GitUtil;
import edu.rit.se.satd.SATDMiner;
import edu.rit.se.satd.detector.SATDDetectorImpl;
import edu.rit.se.satd.writer.MySQLOutputWriter;
import edu.rit.se.util.ElapsedTimer;

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

            ElapsedTimer timer = new ElapsedTimer();
            timer.start();

            SATDMiner miner = new SATDMiner(repo, new SATDDetectorImpl());

            miner.writeRepoSATD(miner.getBaseCommit(null),
                    new MySQLOutputWriter("mySQL.properties"));

            miner.cleanRepo();

            timer.end();
            System.out.println(String.format("Finished analyzing SATD in %s in %,dms",
                    GitUtil.getRepoNameFromGithubURI(repo), timer.readMS()));
//            System.out.println("Mined " + miner.totalCommitsMined.size() + " commits total.");
        }
    }
}
