package io.mopl.global.scheduler;

import org.springframework.batch.core.Job;

public interface BatchTask {
  String getCron();

  String getJobName();

  Job getJob();
}
