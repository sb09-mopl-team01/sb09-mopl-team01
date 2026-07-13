package io.mopl.infra.outbox;

public enum OutboxStatus {
  PENDING,
  CLAIMED,
  PUBLISHED,
  FAILED
}
