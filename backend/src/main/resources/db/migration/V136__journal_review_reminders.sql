-- Operational watermark only; old clients do not need to send this field.
ALTER TABLE decision_reflection ADD COLUMN review_reminded_on date;
CREATE INDEX idx_reflection_unnotified_review ON decision_reflection(review_on,user_id)
    WHERE reviewed_at IS NULL AND review_on IS NOT NULL
      AND review_reminded_on IS DISTINCT FROM review_on;
