package edu.rit.se.satd.mining.ui;

import lombok.NoArgsConstructor;

/**
 * A utility class which provides performance reporting on the
 * runtime duration of various activities done by the system
 */
@NoArgsConstructor
public class ElapsedTimer {

    private long startTime = -1;
    private long endTime = -1;

    public void start() {
        if( startTime == -1 ) {
            this.startTime = System.currentTimeMillis();
        } else {
            System.err.println("\nTried to start timer that was already started.");
        }
    }

    public void end() {
        if( endTime == -1 && startTime != -1 ) {
            this.endTime = System.currentTimeMillis();
        } else {
            System.err.println("\nTried to end time that was already ended or hasn't been started.");
        }
    }

    public long readMS() {
        if( this.endTime != -1 && this.startTime != -1 ) {
            return this.endTime - this.startTime;
        }
        System.err.println("\nTried to read time from incomplete timer.");
        return -1;
    }


}
