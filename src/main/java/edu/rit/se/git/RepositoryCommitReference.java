package edu.rit.se.git;

import edu.rit.se.satd.comment.GroupedComment;
import edu.rit.se.satd.comment.RepositoryComments;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.util.JavaParseUtil;
import edu.rit.se.util.KnownParserException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class used to represent a commit inside a git repository
 */
@RequiredArgsConstructor
public class RepositoryCommitReference {

    @Getter
    final private Git gitInstance;
    @Getter
    final private String projectName;
    @Getter
    final private String projectURI;
    @Getter
    final private RevCommit commit;

    private Map<String, List<GroupedComment>> satdOccurrences = null;

    /**
     * @return A list of the commit's parents
     */
    public List<RepositoryCommitReference> getParentCommitReferences() {
        final RevWalk rw = new RevWalk(this.gitInstance.getRepository());
        return Arrays.stream(this.commit.getParents())
                .map(RevCommit::toObjectId)
                .map(id -> {
                        try {
                            return rw.parseCommit(id);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }})
                .map(parent -> new RepositoryCommitReference(
                        this.gitInstance,
                        this.projectName,
                        this.projectURI,
                        parent
                ))
                .collect(Collectors.toList());
    }

    /**
     * @param detector a detector to classify comments in the files as SATD
     * @param filesToSearch a list of files to limit the search to
     * @return a mapping of files to the SATD Occurrences in each of those files
     */
    public Map<String, RepositoryComments> getFilesToSATDOccurrences(
            SATDDetector detector, List<String> filesToSearch) {
        final TreeWalk thisRepoWalker = GitUtil.getTreeWalker(this.gitInstance, this.commit);
        final Map<String, RepositoryComments> filesToSATDMap = new HashMap<>();
        try {
            // Walk through each Java file in the repository at the time of the commit
            while (thisRepoWalker.next()) {

                final String curFileName = thisRepoWalker.getPathString();

                if( filesToSearch.contains(curFileName)) {
                    // Get loader to load file contents into memory
                    final ObjectLoader fileLoader = this.gitInstance.getRepository()
                            .open(thisRepoWalker.getObjectId(0));
                    final RepositoryComments comments = new RepositoryComments();
                    try {
                        comments.addComments(
                                JavaParseUtil.parseFileForComments(fileLoader.openStream(), curFileName).stream()
                                        .filter(groupedComment -> detector.isSATD(groupedComment.getComment()))
                                        .collect(Collectors.toList()));
                    } catch (KnownParserException e) {
                        comments.addParseErrorFile(e.getFileName());
                    }
                    // Parse Java file for SATD and add it to the map
                    filesToSATDMap.put(
                            curFileName,
                            comments
                    );
                }
            }
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
            e.printStackTrace();
        }

        return filesToSATDMap;
    }

    public String getCommitHash() {
        return this.commit.getName();
    }

    public int getCommitTime() {
        return this.commit.getCommitTime();
    }

    @Override
    public int hashCode() {
        return this.getCommit().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if( obj instanceof  RepositoryCommitReference ) {
            return this.getCommit().hashCode() == ((RepositoryCommitReference) obj).getCommit().hashCode();
        }
        return false;
    }
}
