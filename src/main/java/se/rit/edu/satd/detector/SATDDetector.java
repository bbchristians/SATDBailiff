package se.rit.edu.satd.detector;

public interface SATDDetector {

    /**
     * Given a string comment, determine if the comment contains any SATD
     * @param satd a String comment
     * @return True if the comment contains any SATD, else False
     */
    boolean isSATD(String satd);
}
