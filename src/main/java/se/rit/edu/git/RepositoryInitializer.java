package se.rit.edu.git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import se.rit.edu.util.ElapsedTimer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A class that, given a git repository, collects references to all tags in that repository
 * and supplies a list of those references for further processing.
 */
public class RepositoryInitializer {

    // Git command constants
    private static final String REMOTE = "remote";
    private static final String ORIGIN = "origin";
    private static final String URL = "url";
    private static final String GIT_USERNAME = "u";
    private static final String GIT_PASSWORD = "p";

    // Program constants
    private static final String REPO_OUT_DIR = "repos";

    // Constructor fields
    private String repoDir;
    private String gitURI;

    // Set after initialization
    private Git repoRef = null;

    // Timer for reporting
    private ElapsedTimer cloneTimer = null;
    private ElapsedTimer tagsTimer = null;

    // Prevents other functionality of the class from being used if the git init fails
    private Boolean gitDidInit = false;

    public RepositoryInitializer(String uri, String baseName) {
        this.repoDir = String.join(File.separator, REPO_OUT_DIR, baseName);
        this.gitURI = uri;
    }

    /**
     * Initializes the repository, which:
     * 1. Clones the repository locally (Don't forget to clean it up)
     * 2. Sets the remote reference for the repository
     * @return True if the initialization was successful, else False
     */
    public boolean initRepo() {
        final File newGitRepo = new File(this.repoDir);
        if( newGitRepo.exists() ) {
            this.cleanRepo();
        }
        newGitRepo.mkdirs();
        try {
            this.startCloneElapsedTimer();
            // Clone an instance of the repository locally
            this.repoRef = Git.cloneRepository()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USERNAME, GIT_PASSWORD))
                    .setURI(this.gitURI)
                    .setDirectory(newGitRepo)
                    .setCloneAllBranches(false)
                    .call();
            // Add a remote instance to the repository (to be used for tag listing)
            this.repoRef.getRepository().getConfig().setString(REMOTE, ORIGIN, URL, this.gitURI);
            this.repoRef.getRepository().getConfig().save();
            this.endCloneElapsedTimer();
            this.gitDidInit = true;
        } catch (GitAPIException e) {
            System.err.println("Git API error in git init. Repository will be skipped.");
        } catch (IOException e) {
            System.err.println("IOException when setting remote in gew repo.");
        }
        return this.gitDidInit;
    }

    /**
     * Users git tags to generate a list of repository references
     * @param startCommit The latest commit to consider
     * @return a list of repository references for each tag in the repository
     */
    public List<RepositoryCommitReference> getComparableRepositories(String startCommit) {
        if( !this.gitDidInit ) {
            throw new IllegalStateException("Tried to get repositories from initializer that failed initialization.");
        }
        try {
            this.startGetTagsElapsedTimer();
            // Get remote reference
            final Collection<Ref> remoteRefs = this.repoRef.lsRemote()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USERNAME, GIT_PASSWORD))
                    .setRemote(ORIGIN)
                    .setTags(true)
                    .setHeads(false)
                    .call();
            final RevWalk revWalk = new RevWalk(this.repoRef.getRepository());

            // Get startCommitDate
            final Date latestDate = startCommit != null ?
                    revWalk.parseCommit(this.repoRef.getRepository().resolve(startCommit))
                            .getAuthorIdent()
                            .getWhen()
                    : new Date();

            // Map all valid commits to the date they were made
            final List<CommitData> commitData = remoteRefs.stream().map(ref -> {
                        try {
                            final RevCommit commit = revWalk.parseCommit(ref.getObjectId());
                            final String[] refNameSplit = ref.getName().split("/");
                            return new CommitData(
                                    commit.getName(),
                                    commit.getAuthorIdent().getWhen(),
                                    refNameSplit[refNameSplit.length - 1]);
                        } catch (IOException e) {
                            System.err.println("Error when parsing git tags");
                            e.printStackTrace();
                            return null;
                        } catch (IllegalCharsetNameException e) {
                            System.err.println("Illegal charset given in commit metadata: " + e.getCharsetName());
                            return null;
                        }
                    })
                    .filter(commit -> commit != null && commit.getDate().before(latestDate))
                    .sorted() // TODO updated sorting here?
                    .collect(Collectors.toList());

            revWalk.dispose();
            this.endGetTagsElapsedTimer();

            // Build reference objects from parsed tags
            return commitData.stream()
                    .map(commitDataObject ->
                            new RepositoryCommitReference(
                                    this.repoRef,
                                    GitUtil.getRepoNameFromGithubURI(this.gitURI),
                                    commitDataObject.getCommit(),
                                    commitDataObject.getTag()))
                    .collect(Collectors.toList());

        } catch (GitAPIException e) {
            System.err.println("Error when fetching tags");
        } catch (IOException e) {
            System.err.println("Error when fetching start commit date.");
        }
        return new ArrayList<>();
    }

    /**
     * Attempts to delete the files generated by the initializer
     */
    public void cleanRepo() {
        if( this.repoRef != null ) {
            this.repoRef.getRepository().close();
        }
        File repo = new File(this.repoDir);
        try {
            FileUtils.deleteDirectory(repo);
        } catch (IOException e) {
            System.err.println("Error deleting git repo");
        }
    }

    public String getRepoDir() {
        return this.repoDir;
    }

    public boolean didInitialize() {
        return this.gitDidInit;
    }

    private void startCloneElapsedTimer() {
        this.cloneTimer = new ElapsedTimer();
        this.cloneTimer.start();
    }

    private void endCloneElapsedTimer() {
        this.cloneTimer.end();
        System.out.println(String.format("Finished cloning: %s in %,dms",
                GitUtil.getRepoNameFromGitURI(this.gitURI), this.cloneTimer.readMS()));
    }

    private void startGetTagsElapsedTimer() {
        this.tagsTimer = new ElapsedTimer();
        this.tagsTimer.start();
    }

    private void endGetTagsElapsedTimer() {
        this.tagsTimer.end();
        System.out.println(String.format("Finished gathering tags for %s in %,dms",
                GitUtil.getRepoNameFromGitURI(this.gitURI), this.tagsTimer.readMS()));
    }


    private class CommitData implements Comparable {
        private String commit;
        private Date date;
        private String tag;
        private CommitData(String commit, Date date, String tag) {
            this.commit = commit;
            this.date = date;
            this.tag = tag;
        }

        private String getCommit() {
            return this.commit;
        }
        private Date getDate() {
            return this.date;
        }
        private String getTag() {
            return this.tag;
        }

        @Override
        public int compareTo(Object o) {
            if( o instanceof CommitData) {
                if( o.equals(this) ) {
                    return 0;
                }
                if( ((CommitData) o).getDate().before(this.getDate()) ) {
                    return 1;
                }
                return -1;
            }
            return 0;
        }
    }
}
