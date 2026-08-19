UPDATE webhook_events
SET payload_json = '{"redacted":true}'
WHERE payload_json <> '{"redacted":true}';
