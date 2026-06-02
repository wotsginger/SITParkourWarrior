package club.sitmc.sitParkourWarrior.map;

import java.util.ArrayList;
import java.util.List;

public class DynamicData {
    private boolean enabled = true;
    private final List<String> states = new ArrayList<>();
    private final List<Integer> intervalSequence = new ArrayList<>();
    private final List<Integer> stateIds = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getStates() {
        return states;
    }

    public List<Integer> getIntervalSequence() {
        return intervalSequence;
    }

    public List<Integer> getStateIds() {
        return stateIds;
    }
}
