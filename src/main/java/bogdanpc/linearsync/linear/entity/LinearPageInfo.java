package bogdanpc.linearsync.linear.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LinearPageInfo(boolean hasNextPage, String endCursor) {}
