package club.sitmc.sitParkourWarrior.map;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParkourMap {
    private final String id;
    private String title;
    private Difficulty difficulty = Difficulty.EASY;
    private Region region;
    private PointLocation start;
    private PointLocation end;
    private final DynamicData dynamicData = new DynamicData();
    private boolean deployed;
    private boolean particlesEnabled = true;
    private boolean soundEnabled = true;
    private final List<Deployment> deployments = new ArrayList<>();
    private NodeType nodeType = NodeType.LEVEL;
    private EndTier endTier = EndTier.NORMAL;
    private final List<PointLocation> forkBranchPoints = new ArrayList<>();

    public ParkourMap(String id) {
        this.id = id;
        this.title = id;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public PointLocation getStart() {
        return start;
    }

    public void setStart(Location start) {
        this.start = PointLocation.fromLocation(start);
    }

    public void setStart(PointLocation start) {
        this.start = start;
    }

    public Location getStartLocation() {
        return start != null ? start.toLocation() : null;
    }

    public PointLocation getEnd() {
        return end;
    }

    public void setEnd(Location end) {
        this.end = PointLocation.fromLocation(end);
    }

    public void setEnd(PointLocation end) {
        this.end = end;
    }

    public Location getEndLocation() {
        return end != null ? end.toLocation() : null;
    }

    public DynamicData getDynamicData() {
        return dynamicData;
    }

    public boolean isDynamicEnabled() {
        return dynamicData.isEnabled();
    }

    public boolean isDeployed() {
        return deployed || !deployments.isEmpty();
    }

    public void setDeployed(boolean deployed) {
        this.deployed = deployed;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public void setParticlesEnabled(boolean particlesEnabled) {
        this.particlesEnabled = particlesEnabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }

    public List<Deployment> getDeployments() {
        return Collections.unmodifiableList(deployments);
    }

    public void clearDeployments() {
        deployments.clear();
    }

    public void addDeployment(Deployment deployment) {
        if (deployment == null) {
            return;
        }
        deployments.add(deployment);
    }

    public boolean removeDeployment(String deploymentId) {
        if (deploymentId == null) {
            return false;
        }
        return deployments.removeIf(d -> deploymentId.equals(d.getId()));
    }

    public Deployment getDeployment(String deploymentId) {
        if (deploymentId == null) {
            return null;
        }
        for (Deployment deployment : deployments) {
            if (deploymentId.equals(deployment.getId())) {
                return deployment;
            }
        }
        return null;
    }

    public Deployment findDeploymentByLocation(Location location) {
        if (location == null) {
            return null;
        }
        for (Deployment deployment : deployments) {
            Region region = deployment.getRegion();
            if (region != null && region.contains(location)) {
                return deployment;
            }
        }
        return null;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType != null ? nodeType : NodeType.LEVEL;
    }

    public EndTier getEndTier() {
        return endTier;
    }

    public void setEndTier(EndTier endTier) {
        this.endTier = endTier != null ? endTier : EndTier.NORMAL;
    }

    public List<PointLocation> getForkBranchPoints() {
        return forkBranchPoints;
    }
}
