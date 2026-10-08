package com.limoz.fleet.telematics.dto;

import com.limoz.fleet.telematics.FuelSensorStatus;
import com.limoz.fleet.telematics.GpsStatus;

public record DeviceFilter(GpsStatus gpsStatus, FuelSensorStatus fuelSensorStatus, Boolean active) {}
