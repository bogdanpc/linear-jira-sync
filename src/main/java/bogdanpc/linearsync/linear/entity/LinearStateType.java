package bogdanpc.linearsync.linear.entity;

/**
 * Linear issue state types used for filtering issues by their workflow state.
 * These values correspond to the 'type' field in Linear's state API.
 */
public enum LinearStateType {
    /**
     * Issues that haven't been started yet (triage, backlog, todo states)
     */
    UNSTARTED("unstarted"),

    /**
     * Issues currently being worked on (in_progress, in_review states)
     */
    STARTED("started"),

    /**
     * Issues that have been completed (done states)
     */
    COMPLETED("completed"),

    /**
     * Issues that have been cancelled or abandoned
     */
    CANCELED("canceled");

    private final String value;

    LinearStateType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Convert from string value to enum, case-insensitive
     */
    public static LinearStateType fromValue(String value) {
        if (value == null) {
            return null;
        }

        for (LinearStateType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown Linear state type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}