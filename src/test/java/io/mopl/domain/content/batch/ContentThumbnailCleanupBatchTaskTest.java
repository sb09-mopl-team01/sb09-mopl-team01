package io.mopl.domain.content.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;

@ExtendWith(MockitoExtension.class)
class ContentThumbnailCleanupBatchTaskTest {

  @Mock
  private Job contentThumbnailCleanupJob;

  @Test
  void providesCronJobNameAndJob() {
    given(contentThumbnailCleanupJob.getName())
        .willReturn(ContentThumbnailCleanupJobConfig.JOB_NAME);
    ContentThumbnailCleanupBatchTask batchTask = new ContentThumbnailCleanupBatchTask(
        contentThumbnailCleanupJob,
        "0 30 4 * * *"
    );

    assertThat(batchTask.getCron()).isEqualTo("0 30 4 * * *");
    assertThat(batchTask.getJob()).isSameAs(contentThumbnailCleanupJob);
    assertThat(batchTask.getJobName()).isEqualTo(ContentThumbnailCleanupJobConfig.JOB_NAME);
  }

  @Test
  void rejectsInvalidCronAtStartup() {
    assertThatThrownBy(
        () -> new ContentThumbnailCleanupBatchTask(contentThumbnailCleanupJob, "invalid-cron")
    ).isInstanceOf(IllegalArgumentException.class);
  }
}
