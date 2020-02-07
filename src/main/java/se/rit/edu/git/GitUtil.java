package se.rit.edu.git;

public class GitUtil {

    public static String getRepoNameFromGitURI(String gitURI) {
        String[] uriSplit = gitURI.split("/");
        return uriSplit[uriSplit.length - 1].replace(".git", "");
    }
}
