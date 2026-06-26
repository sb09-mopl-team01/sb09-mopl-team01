package io.mopl.domain.review.replica.User;

import io.mopl.global.entity.BaseUpdatableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity(name = "ReviewReplicaUser")
@Table(name = "users")
public class User extends BaseUpdatableEntity {

  private String name = "임시유저";
  private String profileImageUrl = "url";
}
