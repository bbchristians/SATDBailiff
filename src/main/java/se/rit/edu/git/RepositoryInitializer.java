package se.rit.edu.git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import se.rit.edu.util.ElapsedTimer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.IllegalCharsetNameException;
import java.util.*;
import java.util.stream.Collectors;

public class RepositoryInitializer {

    private static final String ORIGIN = "origin";

    private String repoDir;
    private String gitURI;
    private Git repoRef;

    private Boolean gitDidInit = false;

    public RepositoryInitializer(String uri, String baseName) {
        this.repoDir = "repos/" + baseName + "/master";
        this.gitURI = uri;
        File newGitRepo = new File(this.repoDir);
        if( newGitRepo.exists() ) {
            this.cleanRepo();
        }
        newGitRepo.mkdirs();
        try {
            ElapsedTimer timer = new ElapsedTimer();
            timer.start();
            this.repoRef = Git.cloneRepository()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("u", "p"))
                    .setURI(uri)
                    .setDirectory(newGitRepo)
                    .setCloneAllBranches(false)
                    .call();
            StoredConfig config = this.repoRef.getRepository().getConfig();
            config.setString("remote", ORIGIN, "url", uri);
            config.save();
            timer.end();
            System.out.println(String.format("Finished cloning: %s in %6dms",
                    GitUtil.getRepoNameFromGitURI(gitURI), timer.readMS()));
            this.gitDidInit = true;
        } catch (GitAPIException e) {
            System.err.println("Git API error in git init. Repository will be skipped.");
        } catch (IOException e) {
            System.err.println("IOException when setting remote in gew repo.");
        }
    }

    public List<RepositoryCommitReference> getComparableRepositories(String startCommit) {
        if( this.gitDidInit ) {
            try {
                ElapsedTimer timer = new ElapsedTimer();
                timer.start();
                // Get remote reference
                Collection<Ref> remoteRefs = this.repoRef.lsRemote()
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider("u", "p"))
                        .setRemote(ORIGIN)
                        .setTags(true)
                        .setHeads(false)
                        .call();
                final RevWalk revWalk = new RevWalk(this.repoRef.getRepository());

                // Get startCommitDate
                Date latestDate = startCommit != null ? revWalk.parseCommit(this.repoRef.getRepository().resolve(startCommit))
                        .getAuthorIdent()
                        .getWhen()
                        : new Date();

                // Map all valid commits to the date they were made
                final List<CommitData> commitData = remoteRefs.stream().map(ref -> {
                    try {
                        RevCommit commit = revWalk.parseCommit(ref.getObjectId());
                        String[] refNameSplit = ref.getName().split("/");
                        return new CommitData(commit.getName(), commit.getAuthorIdent().getWhen(),
                                refNameSplit[refNameSplit.length - 1]);
                    } catch (IOException e) {
                        System.err.println("Error when parsing git tags");
                        e.printStackTrace();
                        return null;
                    } catch (IllegalCharsetNameException e) {
                        System.err.println("Illegal charset given in commit metadata: " + e.getCharsetName());
                        return null;
                    }
                }).filter(commit -> commit != null && commit.getDate().before(latestDate))
                        .sorted()
                        .collect(Collectors.toList());
                timer.end();
                System.out.println(String.format("Finished gathering tags for %s in %6dms",
                        GitUtil.getRepoNameFromGitURI(this.gitURI), timer.readMS()));

                revWalk.dispose();

                return commitData.stream()
                        .map(commitDataObject -> new RepositoryCommitReference(
                                this.repoRef, GitUtil.getRepoNameFromGithubURI(this.gitURI),
                                commitDataObject.getCommit(), commitDataObject.getTag()))
                        .collect(Collectors.toList());

            } catch (GitAPIException e) {
                System.err.println("Error when fetching tags");
            } catch (IOException e) {
                System.err.println("Error when fetching start commit date.");
            }
        }
        return new ArrayList<>();
    }

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
