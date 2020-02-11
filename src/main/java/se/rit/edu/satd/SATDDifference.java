package se.rit.edu.satd;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SATDDifference {

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
                        csv[6]
                })
                .collect(Collectors.toList());
    }
}
