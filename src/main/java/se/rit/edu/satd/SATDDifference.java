package se.rit.edu.satd;

import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SATDDifference {

    private static final double LEVENSHTEIN_DISTANCE_MIN = 0.75;

    private String oldTag;
    private String newTag;
    private String projectName;

    private List<SATDInstance> fileRemovedSATD = new ArrayList<>();
    private List<SATDInstance> addressedOrChangedSATD = new ArrayList<>();
    private List<SATDInstance> changedOrAddedSATD = new ArrayList<>();
    private List<SATDInstance> unaddressedSATD = new ArrayList<>();

    public SATDDifference(String projectName, String oldTag, String newTag) {
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

    public List<String[]> toCSV() {
        List<String[]> allCSVEntries = new ArrayList<>();
        allCSVEntries.addAll(instanceListToCSV(this.fileRemovedSATD));
        allCSVEntries.addAll(instanceListToCSV(this.unaddressedSATD));
        allCSVEntries.addAll(instanceListToCSV(this.addressedOrChangedSATD));
        allCSVEntries.addAll(instanceListToCSV(this.changedOrAddedSATD));
        return allCSVEntries;
    }

    public void alignRemovedAndAddedForOverlaps() {
        // If it might have been removed, check if it was just changed slightly instead
        this.addressedOrChangedSATD.stream()
                .filter(satd -> satd.getResolution().equals(SATDInstance.SATDResolution.SATD_POSSIBLY_REMOVED))
                .forEach(satd -> {
                    List<SATDInstance> match = this.changedOrAddedSATD.stream()
                            .filter(coaSATD ->
                                    coaSATD.getCommitAdded().equals(satd.getCommitRemoved()) &&
                                            coaSATD.getNewFile().equals(satd.getNameOfFileWhenAddressed()) &&
                                            commentsAreSimilar(coaSATD.getSATDComment(), satd.getSATDComment()))
                            .collect(Collectors.toList());
                    if( !match.isEmpty() ) {
                        // Set the updated comment
                        satd.setCommentChangedTo(match.get(0).getSATDComment());
                        satd.setResolution(SATDInstance.SATDResolution.SATD_CHANGED);
                        satd.setNewFile(match.get(0).getNewFile());
                        // Removed the comment because it wasn't really added, and it's now accounted for
                        this.changedOrAddedSATD.remove(match.get(0));
                    }
                });
        // TODO is this something that is common enough to enable?
//        // If the file was removed, see if the code was placed elsewhere
//        this.fileRemovedSATD
//                .forEach(satd -> {
//                    List<SATDInstance> match = this.changedOrAddedSATD.stream()
//                            .filter(coaSATD -> coaSATD.getSATDComment().equals(satd.getSATDComment()))
//                            .collect(Collectors.toList());
//                    if( !match.isEmpty() ) {
//                        this.changedOrAddedSATD.remove(match.get(0));
//                        satd.setResolution(SATDInstance.SATDResolution.SATD_MOVED_FILE);
//                        satd.setNewFile(match.get(0).getNewFile());
//                    }
//                });
    }

    private static boolean commentsAreSimilar(String comment1, String comment2) {
        if( comment1.isEmpty() && comment2.isEmpty() ) {
            return true;
        }
        return LEVENSHTEIN_DISTANCE_MIN >=
                new LevenshteinDistance().apply(comment1, comment2) / (double)Integer.max(comment1.length(), comment2.length());
    }

    private List<String[]> instanceListToCSV(List<SATDInstance> list) {
        return list.stream()
                .map(SATDInstance::toCSV)
                .map(csv -> new String[] {
                        this.projectName,
                        this.oldTag,
                        this.newTag,
                        csv[0],
                        csv[1],
                        csv[2],
                        csv[3],
                        csv[4],
                        csv[5],
                        csv[6],
                        csv[7]
                })
                .collect(Collectors.toList());
    }
}
