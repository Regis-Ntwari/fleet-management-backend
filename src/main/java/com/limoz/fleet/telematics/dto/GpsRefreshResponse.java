package com.limoz.fleet.telematics.dto;

/** Result of a GPS status refresh over all devices. */
public record GpsRefreshResponse(int devices, int changed, int online, int offline, int noSignal, int disconnected) {}
