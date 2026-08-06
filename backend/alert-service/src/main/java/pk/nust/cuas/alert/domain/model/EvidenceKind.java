package pk.nust.cuas.alert.domain.model;

/** What one piece of an incident's evidence bundle is (ARCHITECTURE.md section 2.4.3). */
public enum EvidenceKind {

    DETECTION,
    TRACK_SNAPSHOT,
    INFERENCE,
    ASSESSMENT,
    /** An EO/IR still. */
    FRAME,
    SPECTROGRAM,
    /** A signed segment of the tamper-evident chain (section 7.5). */
    AUDIT_EXTRACT,
    OPERATOR_NOTE
}
