package io.mopl.domain.user.service;

import io.mopl.domain.user.document.UserDocument;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.user.repository.search.UserSearchRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSearchUserDataInitializer {

  private final UserRepository userRepository;

  @Autowired(required = false)
  private UserSearchRepository userSearchRepository;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional(readOnly = true)
  public void initializeUserData() {
    if (userSearchRepository == null) {
      log.info("OpenSearch is not available. Skipping initial data sync.");
      return;
    }

    try {
      long currentCount = userSearchRepository.count();
      if (currentCount > 0) {
        log.info("OpenSearch already contains {} users. Skipping initialization.", currentCount);
        return;
      }

      log.info("Starting initial data sync from DB to OpenSearch...");

      int pageNumber = 0;
      int pageSize = 500;
      Page<User> userPage;

      do {
        userPage = userRepository.findAll(PageRequest.of(pageNumber, pageSize));

        List<UserDocument> documents = userPage.getContent().stream()
            .map(user -> UserDocument.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isLocked(user.isLocked())
                .createdAt(user.getCreatedAt())
                .build())
            .toList();

        if (!documents.isEmpty()) {
          userSearchRepository.saveAll(documents);
          log.info("Synced {} users to OpenSearch (Page {})", documents.size(), pageNumber);
        }

        pageNumber++;
      } while (userPage.hasNext());

      log.info("Successfully completed initial data sync to OpenSearch.");
    } catch (Exception e) {
      log.error("Failed to initialize OpenSearch data", e);
    }
  }
}
