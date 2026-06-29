package io.mopl.domain.review.replica.Content;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository("reviewContentRepository")
public interface ContentRepository extends JpaRepository<Content, UUID> {
}
