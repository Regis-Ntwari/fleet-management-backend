package com.limoz.fleet.security;

public final class Roles {

    private Roles() {}

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String IT_ADMIN = "IT_ADMIN";
    public static final String MANAGEMENT = "MANAGEMENT";
    public static final String FLEET_MANAGER = "FLEET_MANAGER";
    public static final String FLEET_OFFICER = "FLEET_OFFICER";
    public static final String DISPATCHER = "DISPATCHER";
    public static final String WORKSHOP_MANAGER = "WORKSHOP_MANAGER";
    public static final String TECHNICIAN = "TECHNICIAN";
    public static final String DRIVER = "DRIVER";
    public static final String FINANCE = "FINANCE";
    public static final String COMPLIANCE_OFFICER = "COMPLIANCE_OFFICER";
    public static final String VIEWER = "VIEWER";

    public static String authority(String roleCode) {
        return "ROLE_" + roleCode;
    }
}
