package se.rit.edu.satd;

public class SATDDifference {

    private int fileRemovedSATD = 0;
    private int addressedSATD = 0;
    private int totalSATD = 0;

    public SATDDifference() { }

    public void addFileRemovedSATD(int count) {
        this.fileRemovedSATD += count;
    }

    public void addAddressedSATD(int count) {
        this.addressedSATD += count;
    }

    public void addTotalSATD(int count) {
        this.totalSATD += count;
    }

    public int getFileRemovedSATD() {
        return this.fileRemovedSATD;
    }

    public int getAddressedSATD() {
        return this.addressedSATD;
    }

    public int getTotalSATD() {
        return totalSATD;
    }
}
