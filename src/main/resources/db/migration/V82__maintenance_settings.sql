-- =====================================================================
-- V82: garage dashboard colour-band thresholds (days in garage)
-- =====================================================================

INSERT INTO system_settings (setting_key, setting_value, value_type, category, description, editable) VALUES
 ('maintenance.garage_amber_days','3','INTEGER','MAINTENANCE','Days in garage from which a job is highlighted amber on the garage dashboard',TRUE),
 ('maintenance.garage_red_days','6','INTEGER','MAINTENANCE','Days in garage from which a job is highlighted red on the garage dashboard',TRUE);
