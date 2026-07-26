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
class ContentPopularitySyncBatchTaskTest {

  @Mock
  private Job contentPopularitySyncJob;

  @Test
  void providesCronJobNameAndJob() {
    given(contentPopularitySyncJob.getName())
        .willReturn(ContentPopularitySyncJobConfig.JOB_NAME);
    ContentPopularitySyncBatchTask batchTask = new ContentPopularitySyncBatchTask(
        contentPopularitySyncJob,
        "0 0 * * * *"
    );

    assertThat(batchTask.getCron()).isEqualTo("0 0 * * * *");
    assertThat(batchTask.getJob()).isSameAs(contentPopularitySyncJob);
    assertThat(batchTask.getJobName()).isEqualTo(ContentPopularitySyncJobConfig.JOB_NAME);
  }

  @Test
  void rejectsInvalidCronAtStartup() {
    assertThatThrownBy(
        () -> new ContentPopularitySyncBatchTask(contentPopularitySyncJob, "invalid-cron")
    ).isInstanceOf(IllegalArgumentException.class);
  }
}
