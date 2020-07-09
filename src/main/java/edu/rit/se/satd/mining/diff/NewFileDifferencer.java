package edu.rit.se.satd.mining.diff;

import edu.rit.se.git.GitUtil;
import edu.rit.se.satd.comment.model.GroupedComment;
import edu.rit.se.satd.comment.model.NullGroupedComment;
import edu.rit.se.satd.model.SATDInstance;
import edu.rit.se.satd.model.SATDInstanceInFile;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.jgit.diff.DiffEntry.DEV_NULL;

public class NewFileDifferencer extends FileDifferencer {

    NewFileDifferencer(Git gitInstance) {
        super(gitInstance);
    }

    @Override
    public String getPertinentFilePath(DiffEntry entry) {
        return entry.getNewPath();
    }

    @Override
    public List<SATDInstance> getInstancesFromFile(DiffEntry diffEntry, GroupedComment newComment) {
        final List<SATDInstance> satd = new ArrayList<>();

        switch (diffEntry.getChangeType()) {
            case ADD:
                satd.add(
                        new SATDInstance(
                                new SATDInstanceInFile(DEV_NULL, new NullGroupedComment()),
                                new SATDInstanceInFile(diffEntry.getNewPath(), newComment),
                                SATDInstance.SATDResolution.SATD_ADDED
                        )
                );
                break;
            case MODIFY: case RENAME: case COPY:
                // Determine if the edit to the file touched the SATD
                this.getEdits(diffEntry).stream()
                        .filter(edit -> GitUtil.editOccursInNewFileBetween(
                                edit, newComment.getStartLine(), newComment.getEndLine()))
                        .findAny()
                        .ifPresent(edit ->
                                satd.add(
                                        new SATDInstance(
                                                new SATDInstanceInFile(DEV_NULL, new NullGroupedComment()),
                                                new SATDInstanceInFile(diffEntry.getNewPath(), newComment),
                                                SATDInstance.SATDResolution.SATD_ADDED
                                        ))
                        );
                break;
        }

        return satd;
    }
}
