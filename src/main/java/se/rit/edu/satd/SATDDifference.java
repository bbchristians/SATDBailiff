package se.rit.edu.satd;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private String oldTag;
    private String newTag;
    private String projectName;

    // The lists of the different types of SATD that can be found in a project
    private List<SATDInstance> fileRemovedSATD = new ArrayList<>();
    private List<SATDInstance> addressedOrChangedSATD = new ArrayList<>();
    private List<SATDInstance> changedOrAddedSATD = new ArrayList<>();
    private List<SATDInstance> unaddressedSATD = new ArrayList<>();

    public SATDDifference(@NotNull String projectName, @NotNull String oldTag, @NotNull String newTag) {
        this.projectName = projectName;
        this.oldTag = oldTag;
        this.newTag = newTag;
    }

    public void addFileRemovedSATD(List<SATDInstance> satd) {
        this.fileRemovedSATD.addAll(satd);
    }

    public void addAddressedOrChangedSATD(List<SATDInstance> satd) {
        this.addressedOrChangedSATD.addAll(satd);
    }

    public void addChangedOrAddedSATD(List<SATDInstance> satd) {
        this.changedOrAddedSATD.addAll(satd);
    }

    public void addUnaddressedSATD(List<SATDInstance> satd) {
        this.unaddressedSATD.addAll(satd);
    }

    public List<SATDInstance> getFileRemovedSATD() {
        return fileRemovedSATD;
    }

    public List<SATDInstance> getAddressedOrChangedSATD() {
        return addressedOrChangedSATD;
    }

    public List<SATDInstance> getChangedOrAddedSATD() {
        return changedOrAddedSATD;
    }

    public List<SATDInstance> getUnaddressedSATD() {
        return unaddressedSATD;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public String getOldTag() {
        return this.oldTag;
    }

    public String getNewTag() {
        return this.newTag;
    }

    public void alignRemovedAndAddedForOverlaps() {
        // If it might have been removed, check if it was just changed slightly instead
        this.addressedOrChangedSATD.stream()
                .filter(satd -> satd.getResolution().equals(SATDInstance.SATDResolution.SATD_POSSIBLY_REMOVED))
                .forEach(this::mergeSATDWithChangedOrAddedIfNeeded);
    }

    /**
     * Merges an SATD instance that was possibly removed with an SATD instance which
     * was either changed or added if there is a corresponding SATD instance that is
     * likely to be correlated.
     *
     * If there is a single correlating SATD instance, it will be removed from the list of
     * changed or added SATD, and have its resolution updated. Multiple matches will result
     * in a null-decision where no changes will be made.
     * @param satd The SATD instance that was possibly removed
     */
    private void mergeSATDWithChangedOrAddedIfNeeded(SATDInstance satd) {
        List<SATDInstance> match = this.changedOrAddedSATD.stream()
                .filter(coaSATD -> satdIsLikelyChangedTo(satd, coaSATD))
                .collect(Collectors.toList());
        // If there were multiple matches, then we cannot be sure which it actually is
        // It is also likely in this case that the SATD was duplicated within the same file
        if( match.size() == 1 ) {
            // Set the updated comment
            satd.setCommentChangedTo(match.get(0).getSATDComment());
            satd.setResolution(SATDInstance.SATDResolution.SATD_CHANGED);
            satd.setNewFile(match.get(0).getNewFile());
            // Removed the comment because it wasn't really added, and it's now accounted for
            // In the updated "Changed" SATD Instance
            this.changedOrAddedSATD.remove(match.get(0));
        }
    }

    /**
     * Determines if the two satd comments are likely to have originated from the same SATD
     * instance
     * @param from the original SATD instance
     * @param to the SATD instance which may have originated from the same instance
     * @return True if it is likely that the SATD instances share an origin, else False
     */
    private boolean satdIsLikelyChangedTo(SATDInstance from, SATDInstance to) {
        return to.getCommitAdded().equals(from.getCommitRemoved()) &&
                to.getNewFile().equals(from.getNameOfFileWhenAddressed()) &&
                commentsAreSimilar(to.getSATDComment().trim(), from.getSATDComment().trim());
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
