package club.sitmc.sitParkourWarrior.map;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deployment {
    private final String id;
    private final Region region;
    private final PointLocation start;
    private final PointLocation end;
    private final List<PointLocation> forkBranchPoints;

    public Deployment(String id, Region region, Location start, Location end) {
        this(id, region, PointLocation.fromLocation(start), PointLocation.fromLocation(end), Collections.emptyList());
    }

    public Deployment(String id, Region region, PointLocation start, PointLocation end) {
        this(id, region, start, end, Collections.emptyList());
    }

    public Deployment(String id, Region region, PointLocation start, PointLocation end, List<PointLocation> forkBranchPoints) {
        this.id = id;
        this.region = region;
        this.start = start;
        this.end = end;
        this.forkBranchPoints = Collections.unmodifiableList(new ArrayList<>(forkBranchPoints));
    }

    public String getId() {
        return id;
    }

    public Region getRegion() {
        return region;
    }

    public PointLocation getStart() {
        return start;
    }

    public Location getStartLocation() {
        return start != null ? start.toLocation() : null;
    }

    public PointLocation getEnd() {
        return end;
    }

    public Location getEndLocation() {
        return end != null ? end.toLocation() : null;
    }

    public List<PointLocation> getForkBranchPoints() {
        return forkBranchPoints;
    }
}
