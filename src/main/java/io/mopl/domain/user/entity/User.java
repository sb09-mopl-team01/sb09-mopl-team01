package io.mopl.domain.user.entity;

import io.mopl.global.entity.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseUpdatableEntity {

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Column(length = 50)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String profileImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role = Role.ROLE_USER;

  @Column(nullable = false)
  private boolean locked = false;

  @Builder
  public User(String email, String passwordHash, String name, String profileImageUrl, Role role) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.name = name;
    this.profileImageUrl = profileImageUrl;

    if (role != null) {
      this.role = role;
    }
  }


  public void changePassword(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
  }

  public void updateProfile(String name, String profileImageUrl) {
    if (name != null && !name.isBlank()) {
      this.name = name;
    }
    if (profileImageUrl != null) {
      this.profileImageUrl = profileImageUrl;
    }
  }

  public void lockAccount() {
    this.locked = true;
  }

  public void unlockAccount() {
    this.locked = false;
  }

  public void updateRole(Role role) {
    this.role = role;
  }
}
