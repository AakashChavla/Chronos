package io.chronos.api.job;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JsonPayload(String type, String value, @JsonProperty("null") boolean isNull) {}
