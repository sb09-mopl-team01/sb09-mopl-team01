package io.mopl.infra.external.sports;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TheSportsDbEventItem(
    @JsonProperty("idEvent")
    String idEvent,
    @JsonProperty("strEvent")
    String strEvent,
    @JsonProperty("strEventAlternate")
    String strEventAlternate,
    @JsonProperty("strSport")
    String strSport,
    @JsonProperty("strLeague")
    String strLeague,
    @JsonProperty("strHomeTeam")
    String strHomeTeam,
    @JsonProperty("strAwayTeam")
    String strAwayTeam,
    @JsonProperty("dateEvent")
    String dateEvent,
    @JsonProperty("strTime")
    String strTime,
    @JsonProperty("strThumb")
    String strThumb,
    @JsonProperty("strPoster")
    String strPoster,
    @JsonProperty("strDescriptionEN")
    String strDescriptionEn
) {
}
