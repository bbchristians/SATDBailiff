package se.rit.edu.git;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.util.HashSet;
import java.util.Set;

public class GitUtil {

    public static String getRepoNameFromGithubURI(String githubURI) {
        return githubURI.split(".com/")[1].replace(".git", "");
    }

    // mimic git tag --contains <commit>
    private static Set<Ref> getTagsForCommit(Repository repo,
                                             RevCommit latestCommit) throws Exception {
        final Set<Ref> tags = new HashSet<>();
        final RevWalk walk = new RevWalk(repo);
        walk.reset();
        walk.setTreeFilter(TreeFilter.ANY_DIFF);
        walk.sort(RevSort.TOPO, true);
        walk.sort(RevSort.COMMIT_TIME_DESC, true);
        for (final Ref ref : repo.getTags().values()) {
            final RevObject obj = walk.parseAny(ref.getObjectId());
            final RevCommit tagCommit;
            if (obj instanceof RevCommit) {
                tagCommit = (RevCommit) obj;
            } else if (obj instanceof RevTag) {
                tagCommit = walk.parseCommit(((RevTag) obj).getObject());
            } else {
                continue;
            }
            if (walk.isMergedInto(latestCommit, tagCommit)) {
                tags.add(ref);
            }
        }
        return tags;
    }

}
