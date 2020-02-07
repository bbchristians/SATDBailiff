package se.rit.edu;

import se.rit.edu.git.RepositoryInitializer;
import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.satd.SATDDifference;
import satd_detector.core.utils.SATDDetector;
import se.rit.edu.satd.SATDMiner;

import java.util.List;


public class Main {

    public static void main(String[] args) throws Exception {

        List<RepositoryCommitReference> repos = new SATDMiner("https://github.com/spring-projects/spring-boot.git")
                .getTaggedCommits(10);
        System.out.println("Done Cloning Repositories.");


//        RepositoryCommitReference repo = new RepositoryCommitReference("bootOld",
//                "https://github.com/spring-projects/spring-boot.git",
//                "5d89311a899d1099ef57d65fa147783da7a5b54c");
//        RepositoryCommitReference repo2 = new RepositoryCommitReference("bootNew",
//                "https://github.com/spring-projects/spring-boot.git",
//                "9aea0568077bb1c75feaeec5fc2e4bd4b196407a");
        SATDDetector detector = new SATDDetector();
        SATDDifference diff = repos.get(0).diffAgainstNewerRepository(repos.get(1), detector);
        System.out.println("Total:        " + diff.getTotalSATD());
        System.out.println("Addressed:    " + diff.getAddressedSATD());
        System.out.println("File Removed: " + diff.getFileRemovedSATD());
    }
}
