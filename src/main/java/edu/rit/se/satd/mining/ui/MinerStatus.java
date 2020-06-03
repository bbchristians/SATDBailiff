package edu.rit.se.satd.mining.ui;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

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
    private int lastPrintLen = 0;

    // Fields for time remaining
    private long timeMiningStarted = -1;

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
        this.timeMiningStarted = System.currentTimeMillis();
        this.updateOutput();
    }

    public void beginCleanup() {
        this.status = STATUS_CLEANING_UP;
        this.updateOutput();
    }

    public void setComplete(long msElapsed) {
        this.status = STATUS_COMPLETE;
        System.out.print("\r" + StringUtils.repeat(" ", this.lastPrintLen));
        System.out.println(String.format("\rCompleted analyzing %d diffs in %,dms (%.2fms/diff, %d error%s) -- %s",
                this.nDiffsComplete,
                msElapsed,
                ((float)msElapsed)/this.nDiffsComplete,
                this.nErrorsEncountered,
                this.nErrorsEncountered != 1 ? "s" : "",
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
            String out = String.format("\r%s -- %-20s|%s| %s (%.1f%%, %d/%d, %d error%s) -- %s\r",
                    this.repoName,
                    this.status,
                    this.getLoadBar(),
                    this.getTimeRemaining(),
                    100 * this.getPercComplete(),
                    this.nDiffsComplete,
                    this.nDiffsPromised,
                    this.nErrorsEncountered,
                    nErrorsEncountered != 1 ? "s" : "",
                    this.displayWindow);
            this.lastPrintLen = out.length();
            System.out.print("\r" + StringUtils.repeat(" ", this.lastPrintLen) + "\r" + out);
        }
    }

    private String getLoadBar() {
        return IntStream.range(0, 11)
                .mapToObj(i -> i <= 10 * this.getPercComplete() ? "▰" : "▱")
                .collect(Collectors.joining());
    }

    private String getTimeRemaining() {
        if( this.timeMiningStarted == -1 || this.nDiffsComplete < 1 ) {
            return "-";
        }
        final long estimatedMSRemaining = (System.currentTimeMillis() - this.timeMiningStarted)
                * (this.nDiffsPromised - this.nDiffsComplete)
                / this.nDiffsComplete;
        final long estimatedSecRemaining = estimatedMSRemaining / 1000;
        if( estimatedSecRemaining < 60 ) {
            return String.format("%ds remaining", estimatedSecRemaining);
        }
        final long estimatedMinsRemaining = estimatedSecRemaining / 60;
        return String.format("%d min%s remaining", estimatedMinsRemaining, estimatedMinsRemaining == 1 ? "" : "s");
    }

    private float getPercComplete() {
        return this.nDiffsPromised > 0 ? (float) this.nDiffsComplete / (float) this.nDiffsPromised : 0;
    }
}
