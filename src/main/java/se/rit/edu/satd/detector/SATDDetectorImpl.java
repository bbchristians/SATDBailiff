package se.rit.edu.satd.detector;

/**
 * Maintains a wrapper implementation for the SATDDetector project:
 * https://github.com/Tbabm/SATDDetector-Core
 */
public class SATDDetectorImpl implements SATDDetector {

    // Wrapper implementation for this implementation
    private satd_detector.core.utils.SATDDetector detector;

    public SATDDetectorImpl() {
        this.detector = new satd_detector.core.utils.SATDDetector();
    }

    @Override
    public boolean isSATD(String satd) {
        return this.detector.isSATD(satd);
    }
}
