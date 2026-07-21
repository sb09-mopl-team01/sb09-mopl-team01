-- ===========================================================================
-- 1. 코어 도메인 (Users, Contents)
-- ===========================================================================

CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       created_at TIMESTAMPTZ NOT NULL,
                       updated_at TIMESTAMPTZ NOT NULL,
                       email VARCHAR(50) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       name VARCHAR(50) NOT NULL,
                       profile_image_url TEXT,
                       role VARCHAR(50) NOT NULL DEFAULT 'USER',
                       locked BOOLEAN NOT NULL DEFAULT FALSE,
                       CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_created_at ON users (created_at);
CREATE INDEX idx_users_name ON users (name);

CREATE TABLE contents (
                          id UUID PRIMARY KEY,
                          created_at TIMESTAMPTZ,
                          updated_at TIMESTAMPTZ,
                          type VARCHAR(20) NOT NULL,
                          title VARCHAR(255) NOT NULL,
                          description VARCHAR(2000) NOT NULL,
                          thumbnail_url VARCHAR(2048),
                          thumbnail_key VARCHAR(512),
                          source VARCHAR(30) NOT NULL,
                          external_id VARCHAR(100),
                          last_synced_at TIMESTAMPTZ,
                          average_rating DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                          review_count INTEGER NOT NULL DEFAULT 0,
                          CONSTRAINT uk_contents_source_type_external_id UNIQUE (source, type, external_id)
);

CREATE INDEX idx_contents_type ON contents (type);
CREATE INDEX idx_contents_created_at_id ON contents (created_at, id);
CREATE INDEX idx_contents_average_rating_id ON contents (average_rating, id);

-- ===========================================================================
-- 2. 유저 관련 연관 도메인 (Social, Follows, Notifications)
-- ===========================================================================

CREATE TABLE social_accounts (
                                 id UUID PRIMARY KEY,
                                 created_at TIMESTAMPTZ NOT NULL,
                                 user_id UUID NOT NULL,
                                 provider VARCHAR(50) NOT NULL,
                                 provider_user_id VARCHAR(255) NOT NULL,
                                 provider_email VARCHAR(255),
                                 CONSTRAINT fk_social_account_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                                 CONSTRAINT uk_social_account_provider_user UNIQUE (provider, provider_user_id),
                                 CONSTRAINT uk_social_account_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_social_account_user_id ON social_accounts (user_id);

CREATE TABLE follows (
                         id UUID PRIMARY KEY,
                         created_at TIMESTAMPTZ NOT NULL,
                         follower_id UUID NOT NULL,
                         followee_id UUID NOT NULL,
                         CONSTRAINT uk_follows_follower_followee UNIQUE (follower_id, followee_id),
                         CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id),
                         CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users(id)
);

CREATE INDEX idx_follows_follower_id ON follows (follower_id);
CREATE INDEX idx_follows_followee_id ON follows (followee_id);

CREATE TABLE notifications (
                               id UUID PRIMARY KEY,
                               created_at TIMESTAMPTZ NOT NULL,
                               receiver_id UUID NOT NULL,
                               title VARCHAR(100) NOT NULL,
                               content VARCHAR(500) NOT NULL,
                               level VARCHAR(20) NOT NULL,
                               read BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_notifications_receiver_created_at_id ON notifications (receiver_id, created_at, id);

-- ===========================================================================
-- 3. 콘텐츠 연관 도메인 (Tags, Reviews, Watching Sessions, Playlists)
-- ===========================================================================

CREATE TABLE content_tags (
                              content_id UUID NOT NULL,
                              tag VARCHAR(50) NOT NULL,
                              CONSTRAINT fk_content_tags_content FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
                              CONSTRAINT uk_content_tags_content_id_tag UNIQUE (content_id, tag)
);

CREATE INDEX idx_content_tags_tag_content_id ON content_tags (tag, content_id);

CREATE TABLE reviews (
                         id UUID PRIMARY KEY,
                         user_id UUID NOT NULL REFERENCES users(id),
                         content_id UUID NOT NULL REFERENCES contents(id),
                         text VARCHAR(1000) NOT NULL,
                         rating DOUBLE PRECISION NOT NULL,
                         created_at TIMESTAMPTZ NOT NULL,
                         updated_at TIMESTAMPTZ NOT NULL,
                         CONSTRAINT uk_review_author_content UNIQUE (user_id, content_id)
);

CREATE INDEX idx_review_content_id ON reviews(content_id);
CREATE INDEX idx_review_user_id ON reviews(user_id);

CREATE TABLE watching_sessions (
                                   id UUID PRIMARY KEY,
                                   created_at TIMESTAMPTZ NOT NULL,
                                   watcher_id UUID NOT NULL,
                                   content_id UUID NOT NULL,
                                   CONSTRAINT uk_watching_sessions_watcher_id UNIQUE (watcher_id),
                                   CONSTRAINT fk_watching_sessions_watcher FOREIGN KEY (watcher_id) REFERENCES users (id),
                                   CONSTRAINT fk_watching_sessions_content FOREIGN KEY (content_id) REFERENCES contents (id)
);

CREATE INDEX idx_watching_sessions_content_created_at_id ON watching_sessions (content_id, created_at, id);

CREATE TABLE playlists (
                           id UUID PRIMARY KEY,
                           owner_id UUID NOT NULL REFERENCES users(id),
                           title VARCHAR(255) NOT NULL,
                           description VARCHAR(1000),
                           subscriber_count BIGINT DEFAULT 0,
                           created_at TIMESTAMPTZ NOT NULL,
                           updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_playlists_owner_id ON playlists(owner_id);
CREATE INDEX idx_playlists_updated_at ON playlists(updated_at DESC, id DESC);

CREATE TABLE playlist_subscriptions (
                                        id UUID PRIMARY KEY,
                                        playlist_id UUID NOT NULL REFERENCES playlists(id),
                                        user_id UUID NOT NULL REFERENCES users(id),
                                        created_at TIMESTAMPTZ NOT NULL,
                                        CONSTRAINT uk_subscriptions UNIQUE (playlist_id, user_id)
);

CREATE INDEX idx_ps_user_id ON playlist_subscriptions(user_id);

-- ===========================================================================
-- 4. 다이렉트 메시지 시스템 (Chat)
-- ===========================================================================

CREATE TABLE conversations (
                               id UUID PRIMARY KEY,
                               created_at TIMESTAMPTZ NOT NULL,
                               participant_a_id UUID NOT NULL,
                               participant_b_id UUID NOT NULL,
                               CONSTRAINT uk_conversation_participants UNIQUE (participant_a_id, participant_b_id),
                               CONSTRAINT ck_conversations_different_participants CHECK (participant_a_id <> participant_b_id),
                               CONSTRAINT fk_conversations_participant_a FOREIGN KEY (participant_a_id) REFERENCES users (id),
                               CONSTRAINT fk_conversations_participant_b FOREIGN KEY (participant_b_id) REFERENCES users (id)
);

CREATE INDEX idx_conversations_participant_a_created_at_id ON conversations (participant_a_id, created_at, id);
CREATE INDEX idx_conversations_participant_b_created_at_id ON conversations (participant_b_id, created_at, id);

CREATE TABLE direct_messages (
                                 id UUID PRIMARY KEY,
                                 created_at TIMESTAMPTZ NOT NULL,
                                 conversation_id UUID NOT NULL,
                                 sender_id UUID NOT NULL,
                                 receiver_id UUID NOT NULL,
                                 content VARCHAR(1000) NOT NULL,
                                 read BOOLEAN NOT NULL DEFAULT FALSE,
                                 CONSTRAINT fk_direct_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
                                 CONSTRAINT fk_direct_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
                                 CONSTRAINT fk_direct_messages_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
                                 CONSTRAINT ck_direct_messages_different_participants CHECK (sender_id <> receiver_id)
);

CREATE INDEX idx_direct_messages_conversation_created_at_id ON direct_messages (conversation_id, created_at, id);
CREATE INDEX idx_direct_messages_receiver_read_created_at_id ON direct_messages (receiver_id, read, created_at, id);

-- ===========================================================================
-- 5. Spring Batch 프레임워크 메타 데이터 테이블
-- ===========================================================================

CREATE TABLE BATCH_JOB_INSTANCE  (
                                     JOB_INSTANCE_ID BIGINT PRIMARY KEY,
                                     VERSION BIGINT,
                                     JOB_NAME VARCHAR(100) NOT NULL,
                                     JOB_KEY VARCHAR(32) NOT NULL
);

CREATE TABLE BATCH_JOB_EXECUTION  (
                                      JOB_EXECUTION_ID BIGINT PRIMARY KEY,
                                      VERSION BIGINT,
                                      JOB_INSTANCE_ID BIGINT NOT NULL,
                                      CREATE_TIME TIMESTAMP NOT NULL,
                                      START_TIME TIMESTAMP DEFAULT NULL,
                                      END_TIME TIMESTAMP DEFAULT NULL,
                                      STATUS VARCHAR(10),
                                      EXIT_CODE VARCHAR(20),
                                      EXIT_MESSAGE VARCHAR(2500),
                                      LAST_UPDATED TIMESTAMP,
                                      CONSTRAINT JOB_INSTANCE_EXECUTION_FK FOREIGN KEY (JOB_INSTANCE_ID) REFERENCES BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS  (
                                             JOB_EXECUTION_ID BIGINT NOT NULL,
                                             PARAMETER_NAME VARCHAR(100) NOT NULL,
                                             PARAMETER_TYPE VARCHAR(100) NOT NULL,
                                             PARAMETER_VALUE VARCHAR(2500),
                                             IDENTIFYING CHAR(1) NOT NULL,
                                             CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (JOB_EXECUTION_ID) REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION  (
                                       STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
                                       VERSION BIGINT NOT NULL,
                                       STEP_NAME VARCHAR(100) NOT NULL,
                                       JOB_EXECUTION_ID BIGINT NOT NULL,
                                       START_TIME TIMESTAMP NOT NULL,
                                       END_TIME TIMESTAMP DEFAULT NULL,
                                       STATUS VARCHAR(10),
                                       COMMIT_COUNT BIGINT,
                                       READ_COUNT BIGINT,
                                       FILTER_COUNT BIGINT,
                                       WRITE_COUNT BIGINT,
                                       READ_SKIP_COUNT BIGINT,
                                       WRITE_SKIP_COUNT BIGINT,
                                       PROCESS_SKIP_COUNT BIGINT,
                                       ROLLBACK_COUNT BIGINT,
                                       EXIT_CODE VARCHAR(2500),
                                       EXIT_MESSAGE VARCHAR(2500),
                                       LAST_UPDATED TIMESTAMP,
                                       CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (JOB_EXECUTION_ID) REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT  (
                                              JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
                                              SHORT_CONTEXT VARCHAR(2500) NOT NULL,
                                              SERIALIZED_CONTEXT TEXT,
                                              CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (JOB_EXECUTION_ID) REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT  (
                                               STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
                                               SHORT_CONTEXT VARCHAR(2500) NOT NULL,
                                               SERIALIZED_CONTEXT TEXT,
                                               CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (STEP_EXECUTION_ID) REFERENCES BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
);
