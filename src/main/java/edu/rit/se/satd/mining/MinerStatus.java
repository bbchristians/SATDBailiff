package edu.rit.se.satd.mining;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
public class MinerStatus {

    private static final String STATUS_INITIALIZING = "Initializing";
    private static final String STATUS_CALCULATING_DIFFS = "Calculating Diffs";
    private static final String STATUS_MINING_SATD = "Mining SATD";
    private static final String STATUS_CLEANING_UP = "Cleaning Up";
    private static final String STATUS_COMPLETE = "Complete";
    private static final String STATUS_ERROR = "Error";

    @NonNull
    private String repoName;

    private int nDiffsPromised = 0;
    private int nDiffsComplete = 0;
    private int nErrorsEncountered = 0;
    private String displayWindow = STATUS_INITIALIZING;
    private String status = STATUS_INITIALIZING;

    private boolean outputEnabled = true;

    public void setNDiffsPromised(int promised) {
        this.nDiffsPromised = promised;
        this.updateOutput();
    }

    public void fulfilDiffPromise() {
        this.nDiffsComplete++;
        this.updateOutput();
    }

    public void addErrorEncountered() {
        this.nErrorsEncountered++;
        this.updateOutput();
    }

    public void setDisplayWindow(String text) {
        this.displayWindow = text;
        this.updateOutput();
    }

    public void beginInitialization() {
        this.status = STATUS_INITIALIZING;
        this.updateOutput();
    }

    public void beginCalculatingDiffs() {
        this.status = STATUS_CALCULATING_DIFFS;
        this.updateOutput();
    }

    public void beginMiningSATD() {
        this.status = STATUS_MINING_SATD;
        this.updateOutput();
    }

    public void beginCleanup() {
        this.status = STATUS_CLEANING_UP;
        this.updateOutput();
    }

    public void setComplete(long msElapsed) {
        this.status = STATUS_COMPLETE;
        System.out.println(String.format("\rCompleted analyzing %d diffs in %,dms (%.2fms/diff) -- %s",
                this.nDiffsComplete,
                msElapsed,
                ((float)msElapsed)/this.nDiffsComplete,
                this.repoName)
        );
    }

    public void setError() {
        this.status = STATUS_ERROR;
        System.err.println(String.format("\rError analyzing %s", this.repoName));
    }

    public void setOutputEnabled(boolean enabled) {
        this.outputEnabled = enabled;
    }

    private void updateOutput() {
        if( outputEnabled ) {
            System.out.print(String.format("\r%s -- %-20s|%s| (%.1f%%, %d/%d, %d error%s) -- %s",
                    this.repoName,
                    this.status,
                    this.getLoadBar(),
                    100 * this.getPercComplete(),
                    this.nDiffsComplete,
                    this.nDiffsPromised,
                    this.nErrorsEncountered,
                    nErrorsEncountered != 1 ? "s" : "",
                    this.displayWindow));
        }
    }

    private String getLoadBar() {
        return IntStream.range(0, 11)
                .mapToObj(i -> i <= 10 * this.getPercComplete() ? "█" : "░")
                .collect(Collectors.joining());
    }

    private float getPercComplete() {
        return this.nDiffsPromised > 0 ? (float) this.nDiffsComplete / (float) this.nDiffsPromised : 0;
    }
}
