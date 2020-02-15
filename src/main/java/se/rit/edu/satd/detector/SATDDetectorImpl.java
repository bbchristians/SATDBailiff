package se.rit.edu.satd.detector;

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
