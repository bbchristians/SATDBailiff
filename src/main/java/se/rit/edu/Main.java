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

            String repo = inFileReader.next();

            if( !repo.endsWith(".git") ) {
                repo += ".git";
            }

            ElapsedTimer timer = new ElapsedTimer();
            timer.start();

            SATDMiner miner = new SATDMiner(repo);
//            List<RepositoryCommitReference> repos = miner.getReposAtReleases(SATDMiner.ReleaseSortType.RELEASE_PARSE);


//            System.out.println(String.format("%d tags found in repository.", repos.size()));

            miner.setSatdDetector(new SATDDetectorImpl());

//            miner.writeRepoSATD(repos, new CSVOutputWriter(
//                    new File(String.join(File.separator, OUT_DIR, GitUtil.getRepoNameFromGithubURI(repo) + ".csv"))));
            miner.writeRepoSATD(miner.getBaseCommit("809e7225e497b1db1594d56155b25ad64bab6dce"),
                    new MySQLOutputWriter("mySQL.properties"));

            miner.cleanRepo();

            timer.end();
            System.out.println(String.format("Finished analyzing SATD in %s in %,dms",
                    GitUtil.getRepoNameFromGithubURI(repo), timer.readMS()));
        }
    }
}
