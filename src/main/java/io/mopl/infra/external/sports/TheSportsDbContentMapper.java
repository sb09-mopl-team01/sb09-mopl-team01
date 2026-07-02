package io.mopl.infra.external.sports;

import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.infra.external.ExternalContentCandidate;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TheSportsDbContentMapper {

  public ExternalContentCandidate toCandidate(TheSportsDbEventItem event) {
    if (event == null) {
      return null;
    }
    Set<String> tags = new LinkedHashSet<>();
    tags.add(ContentType.SPORT.getValue());
    addTag(tags, event.strSport());
    addTag(tags, event.strLeague());
    addTag(tags, event.strHomeTeam());
    addTag(tags, event.strAwayTeam());

    return new ExternalContentCandidate(
        ContentType.SPORT,
        ContentSource.THE_SPORTS_DB,
        event.idEvent(),
        titleOf(event),
        descriptionOf(event),
        firstNotBlank(event.strThumb(), event.strPoster()),
        tags
    );
  }

  private static String titleOf(TheSportsDbEventItem event) {
    String eventName = firstNotBlank(event.strEvent(), event.strEventAlternate());
    if (eventName != null) {
      return eventName;
    }
    if (isBlank(event.strHomeTeam()) || isBlank(event.strAwayTeam())) {
      return null;
    }
    return event.strHomeTeam() + " vs " + event.strAwayTeam();
  }

  private static String descriptionOf(TheSportsDbEventItem event) {
    String description = firstNotBlank(event.strDescriptionEn(), event.strEventAlternate());
    if (description != null) {
      return description;
    }

    StringBuilder builder = new StringBuilder();
    append(builder, event.strLeague());
    append(builder, event.dateEvent());
    append(builder, event.strTime());
    return builder.isEmpty() ? null : builder.toString();
  }

  private static void append(StringBuilder builder, String value) {
    if (isBlank(value)) {
      return;
    }
    if (!builder.isEmpty()) {
      builder.append(" / ");
    }
    builder.append(value.trim());
  }

  private static void addTag(Set<String> tags, String tag) {
    if (!isBlank(tag)) {
      tags.add(tag.trim());
    }
  }

  private static String firstNotBlank(String first, String second) {
    if (!isBlank(first)) {
      return first.trim();
    }
    if (!isBlank(second)) {
      return second.trim();
    }
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
