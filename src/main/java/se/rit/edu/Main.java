package se.rit.edu;

import se.rit.edu.git.GitUtil;
import se.rit.edu.satd.SATDMiner;
import se.rit.edu.satd.detector.SATDDetectorImpl;
import se.rit.edu.satd.writer.MySQLOutputWriter;
import se.rit.edu.util.ElapsedTimer;

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

            SATDMiner miner = new SATDMiner(repo);

            miner.setSatdDetector(new SATDDetectorImpl());

            miner.writeRepoSATD(miner.getBaseCommit("2cf6933774bec5345e4505948fc1be2f75a66153"),
                    new MySQLOutputWriter("mySQL.properties"));

            miner.cleanRepo();

            timer.end();
            System.out.println(String.format("Finished analyzing SATD in %s in %,dms",
                    GitUtil.getRepoNameFromGithubURI(repo), timer.readMS()));
        }
    }
}
