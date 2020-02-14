package se.rit.edu.git.commitlocator;

import org.eclipse.jgit.api.Git;
import se.rit.edu.satd.SATDInstance;

public class SATDUnaddressedCommitLocator extends CommitLocator {

    @Override
    public String findCommitAddressed(Git gitInstance, SATDInstance satdInstance, String v1, String v2) {
        // SATD not addressed, so no commit
        satdInstance.setResolution(SATDInstance.SATDResolution.SATD_UNADDRESSED);
        satdInstance.setNameOfFileWhenAddressed(SATDInstance.FILE_NONE);
        return SATDInstance.NO_COMMIT;
    }

}
