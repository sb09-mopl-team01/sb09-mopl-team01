package io.mopl.domain.review.replica.Content;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Primary
@Repository("reviewReplicaContentRepository")
public interface ContentRepository extends JpaRepository<Content, UUID> {
}
