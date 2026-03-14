package club.sitmc.sitParkourWarrior.session;

import java.util.UUID;

public class ParkourSession {
    private final UUID playerId;
    private final String mapId;
    private final String deploymentId;
    private long startTime;
    private long elapsedMs;
    private boolean started;
    private boolean completed;
    private boolean insideRegion;
    private boolean insideStart;
    private boolean pendingTitleAtStart;

    public ParkourSession(UUID playerId, String mapId, String deploymentId, long startTime) {
        this.playerId = playerId;
        this.mapId = mapId;
        this.deploymentId = deploymentId;
        this.startTime = startTime;
        this.started = false;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getMapId() {
        return mapId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isStarted() {
        return started;
    }

    public void startTimer(long startTime) {
        this.startTime = startTime;
        this.started = true;
    }

    public void stopTimer(long stopTime) {
        if (!started) {
            return;
        }
        this.elapsedMs += Math.max(0L, stopTime - this.startTime);
        this.started = false;
    }

    public void resetTimer() {
        this.startTime = 0L;
        this.elapsedMs = 0L;
        this.started = false;
    }

    public long getElapsedMs(long now) {
        if (started) {
            return elapsedMs + Math.max(0L, now - startTime);
        }
        return elapsedMs;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isInsideRegion() {
        return insideRegion;
    }

    public void setInsideRegion(boolean insideRegion) {
        this.insideRegion = insideRegion;
    }

    public boolean isInsideStart() {
        return insideStart;
    }

    public void setInsideStart(boolean insideStart) {
        this.insideStart = insideStart;
    }

    public boolean isPendingTitleAtStart() {
        return pendingTitleAtStart;
    }

    public void setPendingTitleAtStart(boolean pendingTitleAtStart) {
        this.pendingTitleAtStart = pendingTitleAtStart;
    }

}
