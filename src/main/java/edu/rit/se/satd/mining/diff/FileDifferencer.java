package edu.rit.se.satd.mining.diff;

import edu.rit.se.satd.comment.model.GroupedComment;
import edu.rit.se.satd.model.SATDInstance;
import lombok.AllArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Used to mine all SATDInstances from a file relating to a comment
 */
@AllArgsConstructor
public abstract class FileDifferencer {

    protected Git gitInstance;

    public abstract List<SATDInstance> getInstancesFromFile(DiffEntry d, GroupedComment c);

    List<Edit> getEdits(DiffEntry entry) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(this.gitInstance.getRepository());
        formatter.setContext(0);
        formatter.setDiffAlgorithm(CommitToCommitDiff.diffAlgo);
        try {
            return formatter.toFileHeader(entry).toEditList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public abstract String getPertinentFilePath(DiffEntry entry);
}
