package io.mopl.domain.review.replica.Content;

import io.mopl.global.entity.BaseUpdatableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity(name = "ReviewReplicaContent")
@Table(name = "contents")
public class Content extends BaseUpdatableEntity {

  public void updateAverageRating(double newAverage) {
  }
}
