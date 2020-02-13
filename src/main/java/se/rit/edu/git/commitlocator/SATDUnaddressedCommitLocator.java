package se.rit.edu.git.commitlocator;

import org.eclipse.jgit.api.Git;
import se.rit.edu.satd.SATDInstance;

public class SATDUnaddressedCommitLocator extends CommitLocator {

    @Override
    public String findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2) {
        // TODO
        return SATDInstance.COMMIT_UNKNOWN;
    }

}
