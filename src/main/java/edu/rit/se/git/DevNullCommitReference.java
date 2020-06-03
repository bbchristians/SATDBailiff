package edu.rit.se.git;


import edu.rit.se.satd.comment.model.RepositoryComments;
import edu.rit.se.satd.detector.SATDDetector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.jgit.diff.DiffEntry.DEV_NULL;

/**
 * A null diff reference used for representing the diff in a repository
 * that does not have any commits
 */
public class DevNullCommitReference extends RepositoryCommitReference {

    public DevNullCommitReference() {
        super(null, null, null, null);
    }

    @Override
    public String getCommitHash() {
        return DEV_NULL;
    }

    @Override
    public int getCommitTime() {
        return -1;
    }

    @Override
    public long getAuthoredTime() {
        return -1;
    }

    @Override
    public List<RepositoryCommitReference> getParentCommitReferences() {
        return new ArrayList<>();
    }

    @Override
    public Map<String, RepositoryComments> getFilesToSATDOccurrences(
            SATDDetector detector, List<String> filesToSearch) {
        return new HashMap<>();
    }

    @Override
    public int hashCode() {
        return DEV_NULL.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DevNullCommitReference;
    }
}
