package se.rit.edu.git.commitlocator;

import org.eclipse.jgit.api.Git;
import se.rit.edu.satd.SATDInstance;

public interface CommitLocator {

    String findCommitIntroduced(Git gitInstance, SATDInstance satdInstance, String v1, String v2);

    String findCommitRemoved(Git gitInstance, SATDInstance satdInstance, String v1, String v2);
}
