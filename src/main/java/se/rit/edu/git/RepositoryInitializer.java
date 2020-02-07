package se.rit.edu.git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RepositoryInitializer {

    private static final String ORIGIN = "origin";

    private String repoDir;
    private Git repoRef;

    public RepositoryInitializer(String uri, String baseName) {
        this.repoDir = "repos/" + baseName + "/master";
        File newGitRepo = new File(this.repoDir);
        if( newGitRepo.exists() ) {
            try {
                FileUtils.deleteDirectory(newGitRepo);
            } catch (IOException e) {
                System.err.println("Error deleting se.rit.edu.git repo");
            }
        }
        newGitRepo.mkdirs();
        try {
            this.repoRef = Git.cloneRepository()
                    .setURI(uri)
                    .setDirectory(newGitRepo)
                    .setCloneAllBranches(false)
                    .call();
            System.out.println("Completed master clone.");
            StoredConfig config = this.repoRef.getRepository().getConfig();
            config.setString("remote", ORIGIN, "url", uri);
            config.save();
            System.out.println("Completed remote config update.");
        } catch (GitAPIException e) {
            System.err.println("Git API error in se.rit.edu.git init.");
        } catch (IOException e) {
            System.err.println("IOException when setting remote in gew repo.");
        }
    }


    public List<RepositoryCommitReference> getComparableRepositories(int eachN) {
        return getComparableRepositories(null, eachN);
    }

    public List<RepositoryCommitReference> getComparableRepositories(String startCommit, int eachN) {
        try {
            // Get remote reference
            Collection<Ref> remoteRefs = this.repoRef.lsRemote()
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
            final List<CommitDatePair> commitDatePairs = remoteRefs.stream().map(ref -> {
                try {
                    return revWalk.parseCommit(ref.getObjectId());
                } catch (IOException e) {
                    System.err.println("Error when parsing se.rit.edu.git tags");
                    e.printStackTrace();
                    return null;
                }
            }).filter(commit -> commit != null && commit.getAuthorIdent().getWhen().before(latestDate))
            .map(commit -> new CommitDatePair(commit.getName(), commit.getAuthorIdent().getWhen()))
            .sorted()
            .collect(Collectors.toList());

            return IntStream.range(0, commitDatePairs.size())
                    .filter(n -> n % Math.min(eachN, commitDatePairs.size() - 1) == 0)
                    .mapToObj(commitDatePairs::get)
                    .map(commitDatePair -> new RepositoryCommitReference(
                            this.repoRef, this.repoDir, commitDatePair.getCommit()))
                    .collect(Collectors.toList());

        } catch (GitAPIException e) {
            System.err.println("Error when fetching tags");
        } catch (IOException e) {
            System.err.println("Error when fetching start commit date.");
        }
        return new ArrayList<>();
    }


    private class CommitDatePair implements Comparable {
        private String commit;
        private Date date;
        private CommitDatePair(String commit, Date date) {
            this.commit = commit;
            this.date = date;
        }

        private String getCommit() {
            return this.commit;
        }
        private Date getDate() {
            return this.date;
        }

        @Override
        public int compareTo(Object o) {
            if( o instanceof CommitDatePair ) {
                if( o.equals(this) ) {
                    return 0;
                }
                if( ((CommitDatePair) o).getDate().before(this.getDate()) ) {
                    return 1;
                }
                return -1;
            }
            return 0;
        }
    }
}
