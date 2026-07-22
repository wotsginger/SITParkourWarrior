package club.sitmc.sitParkourWarrior.map;

public enum NodeType {
    LEVEL,
    FORK,
    GLOBAL_START,
    GLOBAL_END,
    BRANCH_END;

    public static NodeType fromString(String value) {
        if (value == null) {
            return LEVEL;
        }
        switch (value.trim().toLowerCase()) {
            case "level":       return LEVEL;
            case "fork":        return FORK;
            case "globalstart": return GLOBAL_START;
            case "globalend":   return GLOBAL_END;
            case "branchend":   return BRANCH_END;
            default:            return LEVEL;
        }
    }

    /**
     * Returns the canonical config string for serialization (no underscores).
     */
    public String toConfigString() {
        switch (this) {
            case LEVEL:        return "level";
            case FORK:         return "fork";
            case GLOBAL_START: return "globalstart";
            case GLOBAL_END:   return "globalend";
            case BRANCH_END:   return "branchend";
            default:           return "level";
        }
    }
}
