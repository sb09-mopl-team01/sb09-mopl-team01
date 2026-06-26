package io.mopl.domain.user.repository;

import static io.mopl.domain.user.entity.QUser.user;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public CursorResponse<User> findUsersByCursor(
      String emailLike, String roleEqual, Boolean isLocked,
      String cursor, UUID idAfter, int limit,
      String sortBy, SortDirection sortDirection) {

    List<User> users = queryFactory
        .selectFrom(user)
        .where(
            containsEmail(emailLike),
            eqRole(roleEqual),
            eqLocked(isLocked),
            cursorCondition(cursor, idAfter, sortBy, sortDirection)
        )
        .orderBy(createOrderSpecifier(sortBy, sortDirection), user.id.asc())
        .limit(limit + 1)
        .fetch();

    boolean hasNext = users.size() > limit;
    if (hasNext) {
      users.remove(limit);
    }

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (!users.isEmpty()) {
      User lastUser = users.get(users.size() - 1);
      nextCursor = extractCursorValue(lastUser, sortBy);
      nextIdAfter = lastUser.getId();
    }

    Long totalCount = queryFactory.select(user.count())
        .from(user)
        .where(containsEmail(emailLike), eqRole(roleEqual), eqLocked(isLocked))
        .fetchOne();

    return new CursorResponse<>(
        users, nextCursor, nextIdAfter, hasNext,
        totalCount != null ? totalCount : 0L, sortBy, sortDirection
    );
  }

  private BooleanExpression cursorCondition(String cursor, UUID idAfter, String sortBy, SortDirection direction) {
    if (cursor == null || idAfter == null) return null;

    boolean isAsc = direction == SortDirection.ASCENDING;

    if ("createdAt".equalsIgnoreCase(sortBy)) {
      Instant cursorTime = Instant.parse(cursor);
      return isAsc
          ? user.createdAt.gt(cursorTime).or(user.createdAt.eq(cursorTime).and(user.id.gt(idAfter)))
          : user.createdAt.lt(cursorTime).or(user.createdAt.eq(cursorTime).and(user.id.gt(idAfter)));
    }

    if ("name".equalsIgnoreCase(sortBy)) {
      return isAsc
          ? user.name.gt(cursor).or(user.name.eq(cursor).and(user.id.gt(idAfter)))
          : user.name.lt(cursor).or(user.name.eq(cursor).and(user.id.gt(idAfter)));
    }

    if ("email".equalsIgnoreCase(sortBy)) {
      return isAsc
          ? user.email.gt(cursor).or(user.email.eq(cursor).and(user.id.gt(idAfter)))
          : user.email.lt(cursor).or(user.email.eq(cursor).and(user.id.gt(idAfter)));
    }

    if ("isLocked".equalsIgnoreCase(sortBy)) {
      Boolean cursorLocked = Boolean.valueOf(cursor);
      return isAsc
          ? user.locked.stringValue().gt(String.valueOf(cursorLocked)).or(user.locked.eq(cursorLocked).and(user.id.gt(idAfter)))
          : user.locked.stringValue().lt(String.valueOf(cursorLocked)).or(user.locked.eq(cursorLocked).and(user.id.gt(idAfter)));
    }

    if ("role".equalsIgnoreCase(sortBy)) {
      Role cursorRole = Role.valueOf(cursor.toUpperCase());
      return isAsc
          ? user.role.stringValue().gt(cursorRole.name()).or(user.role.eq(cursorRole).and(user.id.gt(idAfter)))
          : user.role.stringValue().lt(cursorRole.name()).or(user.role.eq(cursorRole).and(user.id.gt(idAfter)));
    }

    return null;
  }

  private BooleanExpression containsEmail(String emailLike) {
    return (emailLike != null && !emailLike.isBlank()) ? user.email.containsIgnoreCase(emailLike) : null;
  }

  private BooleanExpression eqRole(String roleEqual) {
    if (roleEqual == null || roleEqual.isBlank()) return null;
    try {
      return user.role.eq(Role.valueOf(roleEqual.toUpperCase()));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private BooleanExpression eqLocked(Boolean isLocked) {
    return isLocked != null ? user.locked.eq(isLocked) : null;
  }

  private OrderSpecifier<?> createOrderSpecifier(String sortBy, SortDirection direction) {
    boolean isAsc = direction == SortDirection.ASCENDING;

    if ("name".equalsIgnoreCase(sortBy)) return isAsc ? user.name.asc() : user.name.desc();
    if ("email".equalsIgnoreCase(sortBy)) return isAsc ? user.email.asc() : user.email.desc();
    if ("isLocked".equalsIgnoreCase(sortBy)) return isAsc ? user.locked.asc() : user.locked.desc();
    if ("role".equalsIgnoreCase(sortBy)) return isAsc ? user.role.asc() : user.role.desc();

    return isAsc ? user.createdAt.asc() : user.createdAt.desc();
  }

  private String extractCursorValue(User user, String sortBy) {
    if ("name".equalsIgnoreCase(sortBy)) return user.getName();
    if ("email".equalsIgnoreCase(sortBy)) return user.getEmail();
    if ("isLocked".equalsIgnoreCase(sortBy)) return String.valueOf(user.isLocked());
    if ("role".equalsIgnoreCase(sortBy)) return user.getRole().name();

    return user.getCreatedAt().toString();
  }
}
