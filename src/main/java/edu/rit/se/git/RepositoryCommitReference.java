package edu.rit.se.git;

import edu.rit.se.satd.comment.model.GroupedComment;
import edu.rit.se.satd.comment.model.RepositoryComments;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class used to represent a diff inside a git repository
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

    /**
     * @return A list of the diff's parents
     */
    public List<RepositoryCommitReference> getParentCommitReferences() {
        // Debugging code -- should NOT be included in any releases.
        // Used to start a search at a specific diff
//        if( this.commit.getName().equals("e394516307697ad4ace3d0c0b1155362eeefa2d6") ) {
//            return new ArrayList<>();
//        }
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
            // Walk through each Java file in the repository at the time of the diff
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
                                        // Ignore JavaDocs and Source Code
                                        .filter(gc ->
                                                !gc.getCommentType().equals(GroupedComment.TYPE_JAVADOC))
                                        .filter(gc ->
                                                !gc.getCommentType().equals(GroupedComment.TYPE_COMMENTED_SOURCE))
                                        .filter(gc -> detector.isSATD(gc.getComment()))
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

    public long getAuthoredTime() {
        return this.commit.getAuthorIdent().getWhen().getTime();
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
