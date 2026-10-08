-- =====================================================================
-- V2: permissions, system roles, role-permission matrix, default settings
-- =====================================================================

INSERT INTO permissions (code, module, description) VALUES
 ('DASHBOARD_VIEW','dashboard','View executive dashboard and alert centre'),
 ('VEHICLE_READ','vehicles','View vehicles'),
 ('VEHICLE_CREATE','vehicles','Register vehicles'),
 ('VEHICLE_UPDATE','vehicles','Edit vehicles and change operational status'),
 ('VEHICLE_DELETE','vehicles','Archive vehicles'),
 ('VEHICLE_ODOMETER_CORRECT','vehicles','Approve odometer corrections (decreasing readings)'),
 ('DRIVER_READ','drivers','View drivers'),
 ('DRIVER_MANAGE','drivers','Create and edit drivers'),
 ('ASSIGNMENT_READ','assignments','View vehicle-driver assignments'),
 ('ASSIGNMENT_MANAGE','assignments','Assign and release vehicles to drivers'),
 ('TRIP_READ','trips','View trips'),
 ('TRIP_MANAGE','trips','Plan, dispatch, start, complete and cancel trips'),
 ('BOOKING_READ','bookings','View bookings and deployments'),
 ('BOOKING_MANAGE','bookings','Create, confirm and cancel bookings'),
 ('DISPATCH_MANAGE','bookings','Assign vehicles/drivers to booking slots and dispatch'),
 ('CUSTOMER_READ','customers','View clients, commitments and LPOs'),
 ('CUSTOMER_MANAGE','customers','Manage clients, commitments and LPOs'),
 ('FUEL_READ','fuel','View fuel transactions'),
 ('FUEL_MANAGE','fuel','Record and edit fuel transactions'),
 ('MAINTENANCE_READ','maintenance','View maintenance jobs and schedules'),
 ('MAINTENANCE_MANAGE','maintenance','Create and progress maintenance jobs'),
 ('MAINTENANCE_APPROVE','maintenance','Approve maintenance jobs and parts'),
 ('INVENTORY_READ','maintenance','View spare parts and stock movements'),
 ('INVENTORY_MANAGE','maintenance','Manage spare parts stock'),
 ('INCIDENT_READ','incidents','View incidents and accidents'),
 ('INCIDENT_MANAGE','incidents','Record and investigate incidents'),
 ('FINE_MANAGE','incidents','Record and settle traffic fines'),
 ('DOCUMENT_READ','documents','View vehicle and driver documents'),
 ('DOCUMENT_MANAGE','documents','Manage vehicle and driver documents and certificates'),
 ('FINANCE_READ','finance','View invoices, payments and expenses'),
 ('FINANCE_MANAGE','finance','Create invoices, record payments and expenses'),
 ('EXPENSE_APPROVE','finance','Approve or reject expenses'),
 ('TELEMATICS_READ','telematics','View GPS positions and daily movement analysis'),
 ('TELEMATICS_MANAGE','telematics','Manage telematics devices and imports'),
 ('REPORT_VIEW','reports','View reports'),
 ('REPORT_EXPORT','reports','Export reports to Excel/CSV/PDF'),
 ('NOTIFICATION_READ','notifications','Read own notifications'),
 ('ALERT_MANAGE','notifications','Acknowledge and resolve management alerts'),
 ('AUDIT_VIEW','administration','View the audit log'),
 ('USER_MANAGE','administration','Manage user accounts'),
 ('ROLE_MANAGE','administration','Manage roles and permissions'),
 ('SETTINGS_MANAGE','administration','Change company profile and operational thresholds'),
 ('IMPORT_DATA','administration','Import CSV/Excel data');

INSERT INTO roles (code, name, description, system_role) VALUES
 ('SUPER_ADMIN','Super Administrator','Full system access',TRUE),
 ('IT_ADMIN','IT Administrator','Users, integrations, configuration and system administration',TRUE),
 ('MANAGEMENT','Management','Executive dashboards, alerts and reports',TRUE),
 ('FLEET_MANAGER','Fleet Manager','Fleet operational management',TRUE),
 ('FLEET_OFFICER','Fleet Officer','Vehicle and operational data entry',TRUE),
 ('DISPATCHER','Dispatcher','Bookings, vehicle and driver assignment',TRUE),
 ('WORKSHOP_MANAGER','Workshop Manager','Maintenance and spare parts',TRUE),
 ('TECHNICIAN','Technician','Assigned maintenance jobs',TRUE),
 ('DRIVER','Driver','Own assignments and trips',TRUE),
 ('FINANCE','Finance / Accountant','Billing, payments, fuel and cost reporting',TRUE),
 ('COMPLIANCE_OFFICER','Compliance Officer','Certificates, fines and incidents',TRUE),
 ('VIEWER','Viewer','Read-only access',TRUE);

-- SUPER_ADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

-- helper: grant a list of permission codes to a role
CREATE OR REPLACE FUNCTION grant_permissions(role_code VARCHAR, codes VARCHAR[]) RETURNS VOID AS $$
BEGIN
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = ANY (codes)
    WHERE r.code = role_code
    ON CONFLICT DO NOTHING;
END;
$$ LANGUAGE plpgsql;

SELECT grant_permissions('IT_ADMIN', ARRAY['DASHBOARD_VIEW','VEHICLE_READ','DRIVER_READ','ASSIGNMENT_READ','TRIP_READ','BOOKING_READ','CUSTOMER_READ','FUEL_READ','MAINTENANCE_READ','INVENTORY_READ','INCIDENT_READ','DOCUMENT_READ','FINANCE_READ','TELEMATICS_READ','TELEMATICS_MANAGE','REPORT_VIEW','NOTIFICATION_READ','AUDIT_VIEW','USER_MANAGE','ROLE_MANAGE','SETTINGS_MANAGE','IMPORT_DATA']);
SELECT grant_permissions('MANAGEMENT', ARRAY['DASHBOARD_VIEW','VEHICLE_READ','DRIVER_READ','ASSIGNMENT_READ','TRIP_READ','BOOKING_READ','CUSTOMER_READ','FUEL_READ','MAINTENANCE_READ','INVENTORY_READ','INCIDENT_READ','DOCUMENT_READ','FINANCE_READ','TELEMATICS_READ','REPORT_VIEW','REPORT_EXPORT','NOTIFICATION_READ','ALERT_MANAGE','AUDIT_VIEW']);
SELECT grant_permissions('FLEET_MANAGER', ARRAY['DASHBOARD_VIEW','VEHICLE_READ','VEHICLE_CREATE','VEHICLE_UPDATE','VEHICLE_DELETE','VEHICLE_ODOMETER_CORRECT','DRIVER_READ','DRIVER_MANAGE','ASSIGNMENT_READ','ASSIGNMENT_MANAGE','TRIP_READ','TRIP_MANAGE','BOOKING_READ','BOOKING_MANAGE','DISPATCH_MANAGE','CUSTOMER_READ','CUSTOMER_MANAGE','FUEL_READ','FUEL_MANAGE','MAINTENANCE_READ','MAINTENANCE_MANAGE','MAINTENANCE_APPROVE','INVENTORY_READ','INCIDENT_READ','INCIDENT_MANAGE','FINE_MANAGE','DOCUMENT_READ','DOCUMENT_MANAGE','FINANCE_READ','EXPENSE_APPROVE','TELEMATICS_READ','REPORT_VIEW','REPORT_EXPORT','NOTIFICATION_READ','ALERT_MANAGE','IMPORT_DATA']);
SELECT grant_permissions('FLEET_OFFICER', ARRAY['DASHBOARD_VIEW','VEHICLE_READ','VEHICLE_CREATE','VEHICLE_UPDATE','DRIVER_READ','DRIVER_MANAGE','ASSIGNMENT_READ','ASSIGNMENT_MANAGE','TRIP_READ','TRIP_MANAGE','BOOKING_READ','CUSTOMER_READ','FUEL_READ','FUEL_MANAGE','MAINTENANCE_READ','INCIDENT_READ','INCIDENT_MANAGE','DOCUMENT_READ','DOCUMENT_MANAGE','TELEMATICS_READ','REPORT_VIEW','NOTIFICATION_READ','IMPORT_DATA']);
SELECT grant_permissions('DISPATCHER', ARRAY['DASHBOARD_VIEW','VEHICLE_READ','DRIVER_READ','ASSIGNMENT_READ','ASSIGNMENT_MANAGE','TRIP_READ','TRIP_MANAGE','BOOKING_READ','BOOKING_MANAGE','DISPATCH_MANAGE','CUSTOMER_READ','CUSTOMER_MANAGE','DOCUMENT_READ','MAINTENANCE_READ','TELEMATICS_READ','REPORT_VIEW','NOTIFICATION_READ']);
SELECT grant_permissions('WORKSHOP_MANAGER', ARRAY['DASHBOARD_VIEW','VEHICLE_READ','DRIVER_READ','MAINTENANCE_READ','MAINTENANCE_MANAGE','MAINTENANCE_APPROVE','INVENTORY_READ','INVENTORY_MANAGE','INCIDENT_READ','DOCUMENT_READ','REPORT_VIEW','REPORT_EXPORT','NOTIFICATION_READ']);
SELECT grant_permissions('TECHNICIAN', ARRAY['VEHICLE_READ','MAINTENANCE_READ','MAINTENANCE_MANAGE','INVENTORY_READ','NOTIFICATION_READ']);
SELECT grant_permissions('DRIVER', ARRAY['VEHICLE_READ','TRIP_READ','ASSIGNMENT_READ','BOOKING_READ','INCIDENT_READ','NOTIFICATION_READ']);
SELECT grant_permissions('FINANCE', ARRAY['DASHBOARD_VIEW','VEHICLE_READ','DRIVER_READ','TRIP_READ','BOOKING_READ','CUSTOMER_READ','CUSTOMER_MANAGE','FUEL_READ','FUEL_MANAGE','MAINTENANCE_READ','INVENTORY_READ','INCIDENT_READ','FINANCE_READ','FINANCE_MANAGE','EXPENSE_APPROVE','FINE_MANAGE','REPORT_VIEW','REPORT_EXPORT','NOTIFICATION_READ','IMPORT_DATA']);
SELECT grant_permissions('COMPLIANCE_OFFICER', ARRAY['DASHBOARD_VIEW','VEHICLE_READ','DRIVER_READ','ASSIGNMENT_READ','DOCUMENT_READ','DOCUMENT_MANAGE','INCIDENT_READ','INCIDENT_MANAGE','FINE_MANAGE','REPORT_VIEW','NOTIFICATION_READ']);
SELECT grant_permissions('VIEWER', ARRAY['DASHBOARD_VIEW','VEHICLE_READ','DRIVER_READ','ASSIGNMENT_READ','TRIP_READ','BOOKING_READ','CUSTOMER_READ','FUEL_READ','MAINTENANCE_READ','INVENTORY_READ','INCIDENT_READ','DOCUMENT_READ','FINANCE_READ','TELEMATICS_READ','REPORT_VIEW','NOTIFICATION_READ']);

DROP FUNCTION grant_permissions(VARCHAR, VARCHAR[]);

-- ---------------------------------------------------------------------
-- Default runtime settings (editable by SETTINGS_MANAGE)
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, category, description, editable) VALUES
 ('company.name','LIMOZ Rwanda','STRING','COMPANY','Trading name shown in the application',TRUE),
 ('company.legal_name','LIMOZ Rwanda Ltd','STRING','COMPANY','Registered legal name used on vouchers and invoices',TRUE),
 ('company.tin','','STRING','COMPANY','Tax identification number',TRUE),
 ('company.email','ops@limoz.rw','STRING','COMPANY','Operations contact email',TRUE),
 ('company.phone','+250 788 000 000','STRING','COMPANY','Operations contact phone',TRUE),
 ('company.address','KN 5 Rd, Nyabugogo','STRING','COMPANY','Postal / physical address',TRUE),
 ('company.city','Kigali','STRING','COMPANY','City',TRUE),
 ('company.country','Rwanda','STRING','COMPANY','Country',TRUE),
 ('company.currency','RWF','STRING','COMPANY','Default operating currency',TRUE),
 ('company.timezone','Africa/Kigali','STRING','COMPANY','Operational timezone (display and business-day calculations)',FALSE),

 ('movement.night_start','22:00','TIME','MOVEMENT','Night driving window start (local time)',TRUE),
 ('movement.night_end','05:00','TIME','MOVEMENT','Night driving window end (local time)',TRUE),
 ('movement.excessive_driving_hours','6','INTEGER','MOVEMENT','Flag vehicles driven more than this many hours in a day',TRUE),
 ('movement.high_daily_distance_km','500','INTEGER','MOVEMENT','Flag vehicles travelling more than this distance in a day',TRUE),
 ('movement.idle_days_threshold','3','INTEGER','MOVEMENT','Raise an alert when an available vehicle has not moved for this many days',TRUE),
 ('movement.speed_limit_kph','80','INTEGER','MOVEMENT','Speed above which a trip is flagged for over-speeding',TRUE),
 ('telematics.gps_offline_minutes','120','INTEGER','TELEMATICS','Minutes without GPS communication before a device is considered offline',TRUE),

 ('fuel.variance_tolerance_litres','5','DECIMAL','FUEL','Allowed difference between station litres and sensor-detected litres',TRUE),
 ('fuel.high_consumption_l_per_100km','25','DECIMAL','FUEL','Consumption above this value raises a fuel anomaly alert',TRUE),
 ('fuel.default_price_per_litre','1580','DECIMAL','FUEL','Pre-filled pump price per litre',TRUE),

 ('documents.expiry_warning_days','30','INTEGER','DOCUMENTS','Days before expiry at which a document becomes EXPIRING_SOON',TRUE),
 ('documents.license_expiry_warning_days','30','INTEGER','DOCUMENTS','Days before driver licence expiry to warn',TRUE),
 ('maintenance.due_soon_km','500','INTEGER','MAINTENANCE','Kilometres before next service at which a schedule is DUE_SOON',TRUE),
 ('maintenance.due_soon_days','14','INTEGER','MAINTENANCE','Days before next service date at which a schedule is DUE_SOON',TRUE),
 ('maintenance.default_interval_km','5000','INTEGER','MAINTENANCE','Default service interval in kilometres for new schedules',TRUE),
 ('maintenance.default_interval_days','180','INTEGER','MAINTENANCE','Default service interval in days for new schedules',TRUE),

 ('booking.reminder_hours','24','INTEGER','DISPATCH','Hours before pickup at which an approaching-booking alert is raised',TRUE),
 ('dispatch.require_valid_documents','true','BOOLEAN','DISPATCH','Block dispatch of vehicles with expired required documents',TRUE),
 ('dispatch.require_valid_license','true','BOOLEAN','DISPATCH','Block assignment of drivers with expired licences',TRUE),

 ('finance.tax_rate_percent','18','DECIMAL','FINANCE','VAT applied on invoices',TRUE),
 ('finance.invoice_due_days','30','INTEGER','FINANCE','Default payment term in days',TRUE),
 ('finance.usd_to_rwf_rate','1300','DECIMAL','FINANCE','Exchange rate used to consolidate USD contracts in RWF reports',TRUE),

 ('security.max_failed_logins','5','INTEGER','SECURITY','Failed logins before the account is temporarily locked',TRUE),
 ('security.lockout_minutes','15','INTEGER','SECURITY','Lockout duration in minutes',TRUE),

 ('utilisation.high_percent','85','INTEGER','UTILISATION','Vehicles used on more than this share of days are heavily utilised',TRUE),
 ('utilisation.low_percent','20','INTEGER','UTILISATION','Vehicles used on fewer than this share of days are underutilised',TRUE);
