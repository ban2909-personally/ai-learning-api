CREATE TABLE community_direct_conversations (
 id UUID PRIMARY KEY,
 user_left UUID NOT NULL REFERENCES users(id),user_right UUID NOT NULL REFERENCES users(id),
 initiator_id UUID NOT NULL REFERENCES users(id),
 status VARCHAR(12) NOT NULL CHECK(status IN ('REQUEST','ACTIVE','DECLINED')),
 left_read_sequence BIGINT NOT NULL DEFAULT 0,right_read_sequence BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(user_left<user_right),CHECK(initiator_id IN (user_left,user_right)),UNIQUE(user_left,user_right)
);
CREATE INDEX idx_direct_left_inbox ON community_direct_conversations(user_left,updated_at DESC,id DESC);
CREATE INDEX idx_direct_right_inbox ON community_direct_conversations(user_right,updated_at DESC,id DESC);
CREATE TABLE community_direct_messages (
 sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,id UUID NOT NULL UNIQUE,
 conversation_id UUID NOT NULL REFERENCES community_direct_conversations(id),
 author_id UUID NOT NULL REFERENCES users(id),client_id UUID NOT NULL,
 body VARCHAR(2000) NOT NULL CHECK(length(trim(body)) BETWEEN 1 AND 2000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(conversation_id,author_id,client_id)
);
CREATE INDEX idx_direct_message_history ON community_direct_messages(conversation_id,sequence);
