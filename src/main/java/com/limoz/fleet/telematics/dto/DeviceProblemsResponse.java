package com.limoz.fleet.telematics.dto;

import java.util.List;

/** Devices needing attention: GPS not communicating and faulty fuel sensors. */
public record DeviceProblemsResponse(int gpsProblemCount, int fuelSensorProblemCount,
                                     List<DeviceResponse> gpsProblems, List<DeviceResponse> fuelSensorProblems) {}
