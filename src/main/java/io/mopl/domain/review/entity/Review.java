package io.mopl.domain.review.entity;

//import io.mopl.domain.content.entity.Content;
//import io.mopl.domain.user.entity.User;


import io.mopl.domain.review.replica.Content.Content;
import io.mopl.domain.review.replica.User.User;
import io.mopl.global.entity.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "review",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_review_author_content",
            columnNames = {"author_id", "content_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseUpdatableEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User author;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  @Column(nullable = false, length = 1000)
  private String text;

  @Column(nullable = false)
  private double rating;

  @Builder
  public Review(User author, Content content, String text, double rating) {
    this.author = author;
    this.content = content;
    this.text = text;
    this.rating = rating;
  }

  public void update(String text, double rating) {
    this.text = text;
    this.rating = rating;
  }
}