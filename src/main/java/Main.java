import git.GitRemoteRepository;
import git.GitRepository;
import git.SATDDifference;
import satd_detector.core.utils.SATDDetector;


public class Main {

    public static void main(String[] args) throws Exception {

        GitRepository[] repos = new GitRemoteRepository(
                "https://github.com/spring-projects/spring-boot.git",
                "spring-boot")
                .getComparableRepositories();
        System.out.println("Done Cloning Repositories.");


//        GitRepository repo = new GitRepository("bootOld",
//                "https://github.com/spring-projects/spring-boot.git",
//                "5d89311a899d1099ef57d65fa147783da7a5b54c");
//        GitRepository repo2 = new GitRepository("bootNew",
//                "https://github.com/spring-projects/spring-boot.git",
//                "9aea0568077bb1c75feaeec5fc2e4bd4b196407a");
        SATDDetector detector = new SATDDetector();
        SATDDifference diff = repos[0].diffAgainstNewerRepository(repos[1], detector);
        System.out.println("Total:        " + diff.getTotalSATD());
        System.out.println("Addressed:    " + diff.getAddressedSATD());
        System.out.println("File Removed: " + diff.getFileRemovedSATD());
    }
}
