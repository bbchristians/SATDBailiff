package se.rit.edu.satd.mining;

import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.satd.SATDDifference;
import se.rit.edu.satd.mining.commit.CommitToCommitDiff;
import se.rit.edu.util.GroupedComment;
import se.rit.edu.util.MappingPair;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class RepositoryCommitDiffMiner extends RepositoryDiffMiner {

    private RepositoryCommitDiffMiner() {}

    @Override
    public SATDDifference mineDiff() throws IllegalStateException {

        // Initial diff object
        final SATDDifference diff = super.mineDiff();

        // Get the SATD occurrences for each repo
        // TODO this step is always taking a while to run, we should parse the revisions first to see what the SATD
        //  situation is
        final Map<String, List<GroupedComment>> olderSATD = this.firstRepo.getFilesToSAIDOccurrences(this.satdDetector);
        final Map<String, List<GroupedComment>> newerSATD = this.secondRepo.getFilesToSAIDOccurrences(this.satdDetector);

        // Load the diffs between versions for all unaccounted for SATD
        final CommitToCommitDiff cToCDiff = new CommitToCommitDiff(this.firstRepo, this.secondRepo);
        olderSATD.keySet().stream()
                .flatMap(fileInOldRepo -> olderSATD.get(fileInOldRepo).stream()
                        .filter(comment ->
                                !newerSATD.keySet().contains(fileInOldRepo) ||
                                        !newerSATD.get(fileInOldRepo).contains(comment))
                        .map(comment -> new MappingPair(fileInOldRepo, comment)))
                .forEach(pair -> cToCDiff.loadDiffsFor(diff, pair.getFirst().toString(), (GroupedComment) pair.getSecond()));

        return diff;
    }

    /**
     * Generates an initial RepositoryDiffMiner Object
     * @param repo The earlier of the repository commit references
     * @return a RepositoryDiffMiner object
     */
    public static RepositoryDiffMiner ofFirstRepository(RepositoryCommitReference repo) {
        RepositoryDiffMiner miner = new RepositoryCommitDiffMiner();
        miner.firstRepo = repo;
        return miner;
    }
}
