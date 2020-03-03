package edu.rit.se.satd;

import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


/**
 * A class which stores and categorizes different SATD instances
 * and maintains logic to merge appropriate entries
 */
public class SATDDifference {

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

}
