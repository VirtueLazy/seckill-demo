SET @activity_id = 1;
SET @stock = 50;

DELETE FROM seckill_failed_message WHERE activity_id = @activity_id;
DELETE FROM seckill_order WHERE seckill_activity_id = @activity_id;
UPDATE seckill_activity
SET seckill_stock = @stock
WHERE id = @activity_id;
