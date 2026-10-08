-- =====================================================================
-- V81: runtime settings owned by the booking / dispatch module
-- =====================================================================
INSERT INTO system_settings (setting_key, setting_value, value_type, category, description, editable) VALUES
 ('dispatch.board_horizon_days','7','INTEGER','DISPATCH','Days ahead the dispatcher board lists upcoming jobs',TRUE);
