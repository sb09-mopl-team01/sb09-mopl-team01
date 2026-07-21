package io.mopl.infra.external.sports;

import java.util.List;

public record TheSportsDbEventsResponse(
    List<TheSportsDbEventItem> events
) {
}
