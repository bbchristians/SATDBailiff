package edu.rit.se.satd.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;


/**
 * A class which stores and categorizes different SATD instances
 * and maintains logic to merge appropriate entries
 */
@RequiredArgsConstructor
public class SATDDifference {

    // Required fields for maintaining an SATD Difference object
    @Getter
    @NonNull
    final private String projectName;
    @Getter
    @NonNull
    final private String projectURI;
    @Getter
    final private RevCommit oldCommit;
    @Getter
    final private RevCommit newCommit;

    // The lists of the different types of SATD that can be found in a project
    @Getter
    private List<SATDInstance> satdInstances = new ArrayList<>();

    public void addSATDInstances(List<SATDInstance> satd) {
        this.satdInstances.addAll(satd);
    }

    public SATDDifference usingNewInstances(List<SATDInstance> satd) {
        this.satdInstances = satd;
        return this;
    }

}
