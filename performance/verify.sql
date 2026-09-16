SET @activity_id = 1;

SELECT COUNT(*) AS order_count
FROM seckill_order
WHERE seckill_activity_id = @activity_id;

SELECT user_id, COUNT(*) AS duplicate_count
FROM seckill_order
WHERE seckill_activity_id = @activity_id
GROUP BY user_id
HAVING COUNT(*) > 1;

SELECT seckill_stock AS database_remaining_stock
FROM seckill_activity
WHERE id = @activity_id;

SELECT COUNT(*) AS pending_dead_letter_count
FROM seckill_failed_message
WHERE activity_id = @activity_id
  AND status = 'PENDING';
