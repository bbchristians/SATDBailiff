package se.rit.edu.git;

public class GitUtil {

    public static String getRepoNameFromGitURI(String gitURI) {
        String[] uriSplit = gitURI.split("/");
        return uriSplit[uriSplit.length - 1].replace(".git", "");
    }

    public static String getRepoNameFromGithubURI(String githubURI) {
        return githubURI.split(".com/")[1].replace('/', '_').replace(".git", "");
    }
}
