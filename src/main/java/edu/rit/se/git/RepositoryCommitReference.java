package edu.rit.se.git;

import edu.rit.se.satd.comment.GroupedComment;
import edu.rit.se.satd.detector.SATDDetector;
import edu.rit.se.util.ElapsedTimer;
import edu.rit.se.util.JavaParseUtil;
import lombok.Getter;
import lombok.NonNull;
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
    @NonNull
    private Git gitInstance;
    @Getter
    @NonNull
    private String projectName;
    @Getter
    @NonNull
    private String projectURI;
    @Getter
    @NonNull
    private RevCommit commit;

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
    public Map<String, List<GroupedComment>> getFilesToSATDOccurrences(
            SATDDetector detector, List<String> filesToSearch){
        final TreeWalk thisRepoWalker = GitUtil.getTreeWalker(this.gitInstance, this.commit);
        final Map<String, List<GroupedComment>> filesToSATDMap = new HashMap<>();
        try {
            // Walk through each Java file in the repository at the time of the commit
            while (thisRepoWalker.next()) {

                final String curFileName = thisRepoWalker.getPathString();

                if( filesToSearch.contains(curFileName)) {

                    // See if the parse was cached
                    if( this.satdOccurrences != null && this.satdOccurrences.containsKey(curFileName) ) {
                        filesToSATDMap.put(curFileName, this.satdOccurrences.get(curFileName));
                    } else {
                        // Get loader to load file contents into memory
                        final ObjectLoader fileLoader = this.gitInstance.getRepository()
                                .open(thisRepoWalker.getObjectId(0));
                        // Parse Java file for SATD and add it to the map
                        filesToSATDMap.put(
                                curFileName,
                                JavaParseUtil.parseFileForComments(fileLoader.openStream()).stream()
                                        .filter(groupedComment -> detector.isSATD(groupedComment.getComment()))
                                        .collect(Collectors.toList())
                        );
                    }
                }
            }
        } catch (MissingObjectException | IncorrectObjectTypeException | CorruptObjectException e) {
            System.err.println("Exception in getting tree walker.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IOException in getting tree walker.");
            e.printStackTrace();
        }

        // Store a reference to be returned later to avoid parsing more than once
        this.satdOccurrences = filesToSATDMap;
        return filesToSATDMap;
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
