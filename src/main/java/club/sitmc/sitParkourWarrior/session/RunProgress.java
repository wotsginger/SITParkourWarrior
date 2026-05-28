package club.sitmc.sitParkourWarrior.session;

/**
 * Independent full-course timing state that survives node handoffs.
 * ParkourSession is replaced on each handoff; this object lives in
 * SessionManager and persists across the entire run from GLOBAL_START
 * to GLOBAL_END.
 */
public class RunProgress {
    private final long startTimestamp;

    public RunProgress(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }
}
