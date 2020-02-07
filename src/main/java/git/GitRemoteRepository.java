package git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GitRemoteRepository {

    private static final String ORIGIN = "origin";
    private static final int MIN_DATE_GAP_YEARS = 4;

    private String uri;
    private String baseName;
    private String repoDir;
    private Git repoRef;

    public GitRemoteRepository(String uri, String baseName) {
        this.uri = uri;
        this.baseName = baseName;
        this.repoDir = "repos/" + baseName + "/master";
        File newGitRepo = new File(this.repoDir);
        if( newGitRepo.exists() ) {
            try {
                FileUtils.deleteDirectory(newGitRepo);
            } catch (IOException e) {
                System.err.println("Error deleting git repo");
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
            System.err.println("Git API error in git init.");
        } catch (IOException e) {
            System.err.println("IOException when setting remote in gew repo.");
        }
    }

    public GitRepository[] getComparableRepositories() {
        try {
            Collection<Ref> remoteRefs = this.repoRef.lsRemote()
                    .setRemote(ORIGIN)
                    .setTags(true)
                    .setHeads(false)
                    .call();
            final RevWalk revWalk = new RevWalk(this.repoRef.getRepository());
            final Map<Date, String> commitDateMap = remoteRefs.stream().map(ref -> {
                try {
                    RevCommit commit = revWalk.parseCommit(ref.getObjectId());
                    return new CommitDatePair(commit.getName(), commit.getAuthorIdent().getWhen());
                } catch (IOException e) {
                    System.err.println("Error when parsing git tags");
                    e.printStackTrace();
                    return new CommitDatePair("null", null);
                }
            }).collect(Collectors.toMap(CommitDatePair::getDate, CommitDatePair::getCommit));
            Date mostRecentDate = getMostRecentDate(commitDateMap.keySet());
            System.out.println("Got most recent commit: " + commitDateMap.get(mostRecentDate));
            Set<Date> validDates = commitDateMap.keySet().stream().filter(date -> {
                Calendar cal = new GregorianCalendar();
                cal.setTime(date);
                cal.add(Calendar.YEAR, MIN_DATE_GAP_YEARS);
                return mostRecentDate.after(cal.getTime());
            }).collect(Collectors.toSet());
            Date mostRecentValidDate = getMostRecentDate(validDates);
            System.out.println("Got target start commit: " + commitDateMap.get(mostRecentValidDate));
            return new GitRepository[] {
                    new GitRepository(
                            repoRef, this.repoDir + "/.git", this.baseName, this.uri,
                            commitDateMap.get(mostRecentValidDate)),
                    new GitRepository(
                            repoRef, this.repoDir + "/.git", this.baseName, this.uri,
                            commitDateMap.get(mostRecentDate))
            };
        } catch (GitAPIException e) {
            System.err.println("Error when fetching tags");
        }
        return new GitRepository[2];
    }

    private Date getMostRecentDate(Set<Date> dates) {
        Date mostRecentDate = null;
        for( Date date : dates ) {
            if( mostRecentDate == null || date.after(mostRecentDate) ) {
                mostRecentDate = date;
            }
        }
        return mostRecentDate;
    }


    private class CommitDatePair {
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
    }
}
