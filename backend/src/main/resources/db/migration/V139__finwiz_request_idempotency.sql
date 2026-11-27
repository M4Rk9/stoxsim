-- Optional client-generated UUID; existing clients and historic rows remain valid.
ALTER TABLE finwiz_request_reservation ADD COLUMN client_request_id uuid;
ALTER TABLE finwiz_request_reservation ADD CONSTRAINT uk_finwiz_client_request UNIQUE(user_id,client_request_id);
