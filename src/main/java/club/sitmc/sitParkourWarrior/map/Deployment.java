package club.sitmc.sitParkourWarrior.map;

import org.bukkit.Location;

public class Deployment {
    private final String id;
    private final Region region;
    private final Location start;
    private final Location end;

    public Deployment(String id, Region region, Location start, Location end) {
        this.id = id;
        this.region = region;
        this.start = start;
        this.end = end;
    }

    public String getId() {
        return id;
    }

    public Region getRegion() {
        return region;
    }

    public Location getStart() {
        return start;
    }

    public Location getEnd() {
        return end;
    }
}
