\set ON_ERROR_STOP

SET SESSION AUTHORIZATION 'tms';
BEGIN;

SELECT iris.update_version('5.16.0', '5.17.0');

-- Add failed column to dms_message_view
DROP VIEW dms_message_view;
CREATE VIEW dms_message_view AS
	SELECT d.name, msg_current, cc.description AS condition,
	       fail_time IS NOT NULL AS failed, multi, beacon_enabled,
	       prefix_page, msg_priority,
	       iris.sign_msg_sources(source) AS sources, duration, expire_time
	FROM iris.dms d
	LEFT JOIN iris.controller c ON d.controller = c.name
	LEFT JOIN iris.condition cc ON c.condition = cc.id
	LEFT JOIN iris.sign_message s ON d.msg_current = s.name;
GRANT SELECT ON dms_message_view TO PUBLIC;

-- Add scale column to road_class
ALTER TABLE iris.road_class ADD COLUMN scale REAL;
UPDATE iris.road_class SET scale = 1 WHERE id = 0;
UPDATE iris.road_class SET scale = 2 WHERE id = 1;
UPDATE iris.road_class SET scale = 3 WHERE id = 2;
UPDATE iris.road_class SET scale = 3 WHERE id = 3;
UPDATE iris.road_class SET scale = 4 WHERE id = 4;
UPDATE iris.road_class SET scale = 4 WHERE id = 5;
UPDATE iris.road_class SET scale = 6 WHERE id = 6;
UPDATE iris.road_class SET scale = 3.5 WHERE id = 7;
ALTER TABLE iris.road_class ALTER COLUMN scale SET NOT NULL;

COMMIT;
