package se.rit.edu.satd.mining;

import org.eclipse.jgit.api.Git;
import se.rit.edu.git.RepositoryCommitReference;
import se.rit.edu.git.RepositoryFileMapping;
import se.rit.edu.git.commitlocator.*;
import se.rit.edu.satd.SATDDifference;
import se.rit.edu.satd.SATDInstance;
import se.rit.edu.satd.detector.SATDDetector;
import se.rit.edu.util.ElapsedTimer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RepositoryDiffMiner {

    private RepositoryCommitReference firstRepo;
    private RepositoryCommitReference secondRepo = null;
    private SATDDetector satdDetector = null;

    private RepositoryDiffMiner() {}

    public RepositoryDiffMiner andSecondRepository(RepositoryCommitReference repo) {
        this.secondRepo = repo;
        return this;
    }

    public static RepositoryDiffMiner ofFirstRepository(RepositoryCommitReference repo) {
        RepositoryDiffMiner miner = new RepositoryDiffMiner();
        miner.firstRepo = repo;
        return miner;
    }

    public RepositoryDiffMiner usingDetector(SATDDetector detector) {
        this.satdDetector = detector;
        return this;
    }

    public SATDDifference mineDiff() {
        if( this.secondRepo == null ) {
            System.err.println("Second repo to diff not set, please call andSecondRepository() to set the second repo.");
        }
        if( this.satdDetector == null ) {
            System.err.println("SATD Detector not set, please call usingDetector() to set the SATD Detector.");
        }

        // Get the SATD occurances for each repo
        Map<String, List<String>> olderSATD = this.firstRepo.getFilesToSAIDOccurrences(this.satdDetector);
        Map<String, List<String>> newerSATD = this.secondRepo.getFilesToSAIDOccurrences(this.satdDetector);

        // Timer for reporting
        ElapsedTimer timer = new ElapsedTimer();
        timer.start();

        // Get file mapping for renamed files
        // TODO does this still need to be done now that we're using git diff?
        Map<String, String> fileMapping = RepositoryFileMapping.getFileMapping(olderSATD.keySet(), newerSATD.keySet());

        // Create base diff object
        SATDDifference difference = new SATDDifference(this.firstRepo.getProjectName(),
                this.firstRepo.getTag(), this.secondRepo.getTag());

        // Iterate through all SATD occurrences and determine what to classify it as
        for( String oldKey : olderSATD.keySet() ) {
            String newKey = fileMapping.get(oldKey);
            if( newKey.equals(RepositoryFileMapping.NOT_FOUND) ) {
                difference.addFileRemovedSATD(olderSATD.get(oldKey).stream()
                        .map(comment -> new SATDInstance(oldKey, "dev/null", comment))
                        .collect(Collectors.toList()));
            } else {
                // See which strings in each file are present here
                final List<MappedSATDComment> oldSATDStrings = olderSATD.get(oldKey).stream()
                        .map(MappedSATDComment::new)
                        .collect(Collectors.toList());
                final List<MappedSATDComment> newSATDStrings = newerSATD.get(newKey).stream()
                        .map(MappedSATDComment::new)
                        .collect(Collectors.toList());

                newSATDStrings.forEach(newerSATDMapping -> {
                    oldSATDStrings.forEach(olderSATDMapping -> {
                        if( !olderSATDMapping.isMapped() && !newerSATDMapping.isMapped() &&
                                olderSATDMapping.getComment().equals(newerSATDMapping.getComment()) ) {
                            olderSATDMapping.setMapped();
                            newerSATDMapping.setMapped();
                        }
                    });
                });
                // SATD that appears identically in both new and old files
                List<SATDInstance> untouchedSATD = oldSATDStrings.stream()
                        .filter(MappedSATDComment::isMapped)
                        .map(MappedSATDComment::getComment)
                        .map(comment -> new SATDInstance(oldKey, newKey, comment))
                        .collect(Collectors.toList());
                // SATD that was not in the new file, but was in the old
                List<SATDInstance> changedOrRemovedSATD = oldSATDStrings.stream()
                        .filter(mc -> !mc.isMapped())
                        .map(MappedSATDComment::getComment)
                        .map(comment -> new SATDInstance(oldKey, "Unknown", comment))
                        .collect(Collectors.toList());
                // SATD that was not in the old file, but was in the new
                List<SATDInstance> changedOrAddedSATD = newSATDStrings.stream()
                        .filter(mc -> !mc.isMapped())
                        .map(MappedSATDComment::getComment)
                        .map(comment -> new SATDInstance("Unknown", newKey, comment))
                        .collect(Collectors.toList());

                difference.addUnaddressedSATD(untouchedSATD);
                difference.addAddressedOrChangedSATD(changedOrRemovedSATD);
                difference.addChangedOrAddedSATD(changedOrAddedSATD);
            }
        }

        // Get commits for File Removed SATD
        difference.getFileRemovedSATD().forEach( satd ->
                getCommitsForSATD(satd, new FileRemovedOrRenamedCommitLocator(), true));

        difference.getAddressedOrChangedSATD().forEach( satd ->
                getCommitsForSATD(satd, new SATDRemovedChangedMovedCommitLocator(), true));

        difference.getUnaddressedSATD().forEach( satd ->
                getCommitsForSATD(satd, new SATDUnaddressedCommitLocator(), true));

        difference.getChangedOrAddedSATD().forEach( satd ->
                getCommitsForSATD(satd, new SATDAddedCommitLocator(), false));

        timer.end();
        System.out.println(String.format("Finished diffing against previous version in %6dms", timer.readMS()));

        return difference;
    }



    private void getCommitsForSATD(SATDInstance satd, CommitLocator locator, boolean useOldFilePath) {
        satd.setCommitAdded(
                locator.findCommitIntroduced(this.firstRepo.getGitInstance(), satd,
                        useOldFilePath ? this.firstRepo.getCommit() : this.secondRepo.getCommit(),
                        useOldFilePath ? satd.getOldFile() : satd.getNewFile()));
        satd.setCommitRemoved(
                locator.findCommitAddressed(this.firstRepo.getGitInstance(), satd,
                        this.firstRepo.getCommit(), this.secondRepo.getCommit()));
    }

    private class MappedSATDComment {

        private String comment;
        private boolean isMapped = false;

        private MappedSATDComment(String oldComment) {
            this.comment = oldComment;
        }

        private String getComment() {
            return this.comment;
        }

        private boolean isMapped() {
            return this.isMapped;
        }

        private void setMapped() {
            this.isMapped = true;
        }
    }
}
