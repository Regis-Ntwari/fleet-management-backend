package com.limoz.fleet.telematics.domain;

/** Health of the optional fuel level sensor attached to a telematics device. */
public enum FuelSensorStatus {
    OK, FAULTY, NOT_INSTALLED, UNKNOWN;

    public boolean isProblem() {
        return this == FAULTY;
    }
}
