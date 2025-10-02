package bogdanpc.linearsync.linear.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LinearResponse<T>(
    @JsonProperty("data") Data<T> data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data<T>(
        @JsonProperty("issues") Issues<T> issues
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issues<T>(
        @JsonProperty("nodes") List<T> nodes,
        @JsonProperty("pageInfo") PageInfo pageInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageInfo(
        @JsonProperty("hasNextPage") boolean hasNextPage,
        @JsonProperty("endCursor") String endCursor
    ) {}
}