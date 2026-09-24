package bogdanpc.linearsync.linear.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum LinearStateType {
    TRIAGE("triage"),
    BACKLOG("backlog"),
    UNSTARTED("unstarted"),
    STARTED("started"),
    COMPLETED("completed"),
    CANCELED("canceled");

    private final String value;

    LinearStateType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static LinearStateType fromValue(String value) {
        var type = parse(value);
        if (type == null) {
            throw new IllegalArgumentException("Unknown Linear state type: " + value);
        }
        return type;
    }

    @JsonCreator
    static LinearStateType parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return value;
    }
}