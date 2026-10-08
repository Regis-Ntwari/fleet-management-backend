package com.limoz.fleet.telematics.dto;

import com.limoz.fleet.telematics.domain.FuelSensorStatus;
import com.limoz.fleet.telematics.domain.GpsStatus;

public record DeviceFilter(GpsStatus gpsStatus, FuelSensorStatus fuelSensorStatus, Boolean active) {}
