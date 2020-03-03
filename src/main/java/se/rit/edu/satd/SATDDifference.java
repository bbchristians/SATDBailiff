package se.rit.edu.satd;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import se.rit.edu.git.models.CommitMetaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * A class which stores and categorizes different SATD instances
 * and maintains logic to merge appropriate entries
 */
public class SATDDifference {

    // A constant value which determines the Levenshtein distance which a comment
    // must share with another comment in order to assume one was modified to become
    // the other
    private static final double LEVENSHTEIN_DISTANCE_MIN = 0.50;

    // Required fields for maintaining an SATD Difference object
    private RevCommit oldCommit;
    private RevCommit newCommit;
    private String projectName;
    private String projectURI;

    // The lists of the different types of SATD that can be found in a project
    private List<SATDInstance> satdInstances = new ArrayList<>();

    public SATDDifference(@NotNull String projectName, @NotNull String projectURI,
                          @NotNull RevCommit oldCommit, @NotNull RevCommit newCommit) {
        this.projectName = projectName;
        this.projectURI = projectURI;
        this.oldCommit = oldCommit;
        this.newCommit = newCommit;
    }

    public void addSATDInstances(List<SATDInstance> satd) {
        this.satdInstances.addAll(satd);
    }

    public List<SATDInstance> getSATDInstances() {
        return satdInstances;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public String getProjectURI() {
        return this.projectURI;
    }

    public RevCommit getOldCommit() {
        return oldCommit;
    }

    public RevCommit getNewCommit() {
        return newCommit;
    }


    // TODO move the following code to a helper util for determining changes
    /**
     * Determines if the two satd comments are likely to have originated from the same SATD
     * instance
     * @param from the original SATD instance
     * @param to the SATD instance which may have originated from the same instance
     * @return True if it is likely that the SATD instances share an origin, else False
     */
    private boolean satdIsLikelyChangedTo(SATDInstance from, SATDInstance to) {
        final List<CommitMetaData> allFromCommits = new ArrayList<>(from.getInitialBlameCommits());
        allFromCommits.addAll(from.getCommitsBetweenVersions());
        allFromCommits.add(from.getCommitAddressed());
        final List<CommitMetaData> allToCommits = new ArrayList<>(to.getInitialBlameCommits());
        allToCommits.addAll(to.getCommitsBetweenVersions());
        allToCommits.add(to.getCommitAddressed());
        return !Collections.disjoint(allToCommits, allFromCommits) &&
                to.getNewFile().equals(from.getNewFile()) && // TODO is this right?
                commentsAreSimilar(to.getCommentOld().trim(), from.getCommentOld().trim());
    }

    /**
     * Determines if the two comments are similar enough to constitute them being classified
     * as one being modified into being the other, rather than one being removed and one added
     * in an un-related manner
     * @param comment1 A string comment
     * @param comment2 A string comment
     * @return True if the comments are similar enough, else false
     */
    private static boolean commentsAreSimilar(String comment1, String comment2) {
        if( comment1.isEmpty() && comment2.isEmpty() ) {
            return true;
        }
        return LEVENSHTEIN_DISTANCE_MIN >= new LevenshteinDistance()
                        .apply(comment1, comment2) / (double)Integer.max(comment1.length(), comment2.length());
    }
}
