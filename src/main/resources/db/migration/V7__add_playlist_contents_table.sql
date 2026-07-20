CREATE TABLE playlist_contents (
                                   id UUID PRIMARY KEY,
                                   created_at TIMESTAMPTZ NOT NULL,
                                   updated_at TIMESTAMPTZ NOT NULL,

                                   playlist_id UUID NOT NULL,
                                   content_id UUID NOT NULL,

                                   CONSTRAINT uk_playlist_content UNIQUE (playlist_id, content_id),

                                   CONSTRAINT fk_playlist_contents_playlist
                                       FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE,
                                   CONSTRAINT fk_playlist_contents_content
                                       FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE
);

CREATE INDEX idx_playlist_contents_playlist_id ON playlist_contents (playlist_id);
CREATE INDEX idx_playlist_contents_content_id ON playlist_contents (content_id);