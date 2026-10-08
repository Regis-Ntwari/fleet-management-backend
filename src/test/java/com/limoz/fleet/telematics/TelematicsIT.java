package com.limoz.fleet.telematics;

import com.limoz.fleet.security.Roles;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import com.limoz.fleet.telematics.dto.DeviceRequest;
import com.limoz.fleet.telematics.dto.PositionInput;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelematicsIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired
    TestData data;

    @Autowired
    Clock clock;

    private DeviceRequest deviceRequest(Long vehicleId, String externalId) {
        return new DeviceRequest(vehicleId, "manual", externalId, "+25078" + SEQ.incrementAndGet(), LocalDate.now(clock),
                FuelSensorStatus.OK, "Installed by test");
    }

    private long registerDevice(Long vehicleId, String externalId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/telematics/devices").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(deviceRequest(vehicleId, externalId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private static PositionInput position(Long vehicleId, String plate, String externalId, Instant at, double lat, double lon,
                                          double speed, Double odometer) {
        return new PositionInput(vehicleId, plate, externalId, at, BigDecimal.valueOf(lat), BigDecimal.valueOf(lon),
                BigDecimal.valueOf(speed), null, odometer == null ? null : BigDecimal.valueOf(odometer), speed > 0, null, null);
    }

    @Test
    @DisplayName("a device is registered once per vehicle, audited, and a second registration is rejected with 409")
    void registerDevice() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        String externalId = "UNIT-" + SEQ.incrementAndGet();
        String body = mockMvc.perform(post("/api/v1/telematics/devices").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(deviceRequest(vehicle.id(), externalId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicle.plateNumber").value(vehicle.plateNumber()))
                .andExpect(jsonPath("$.gpsStatus").value("NO_SIGNAL"))
                .andExpect(jsonPath("$.fuelSensorStatus").value("OK"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.externalDeviceId").value(externalId))
                .andReturn().getResponse().getContentAsString();
        long deviceId = json.readTree(body).get("id").asLong();

        mockMvc.perform(post("/api/v1/telematics/devices").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(deviceRequest(vehicle.id(), "UNIT-" + SEQ.incrementAndGet()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        mockMvc.perform(get("/api/v1/audit-logs/entity/TelematicsDevice/" + deviceId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CREATE"));

        mockMvc.perform(patch("/api/v1/telematics/devices/" + deviceId + "/fuel-sensor").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FAULTY\",\"reason\":\"Readings frozen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelSensorStatus").value("FAULTY"))
                .andExpect(jsonPath("$.fuelSensorProblem").value(true));
        mockMvc.perform(get("/api/v1/telematics/devices/problems").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelSensorProblems[*].id").value(hasItem((int) deviceId)));
        mockMvc.perform(get("/api/v1/telematics/devices").header("Authorization", adminToken).param("fuelSensorStatus", "FAULTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem((int) deviceId)));
    }

    @Test
    @DisplayName("a JSON batch reports imported, duplicate and rejected rows, updates the device and the odometer")
    void ingestBatch() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        String externalId = "UNIT-" + SEQ.incrementAndGet();
        long deviceId = registerDevice(vehicle.id(), externalId);
        Instant now = Instant.now(clock);
        Instant tenMinutesAgo = now.minus(Duration.ofMinutes(10));
        List<PositionInput> rows = List.of(
                position(vehicle.id(), null, null, tenMinutesAgo, -1.9441, 30.0619, 35, 15200.0),
                position(null, vehicle.plateNumber(), null, tenMinutesAgo, -1.9441, 30.0619, 35, 15200.0),           // duplicate
                position(vehicle.id(), null, null, now.plus(Duration.ofHours(1)), -1.9441, 30.0619, 35, null),       // future
                position(vehicle.id(), null, null, now.minus(Duration.ofMinutes(8)), 95.0, 30.0619, 35, null),       // bad latitude
                position(null, "ZZZ 000 X", null, now.minus(Duration.ofMinutes(7)), -1.9441, 30.0619, 35, null),     // unknown plate
                position(null, null, externalId, now.minus(Duration.ofMinutes(5)), -1.9500, 30.0700, 42, 15250.4));

        mockMvc.perform(post("/api/v1/telematics/positions").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(rows)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(6))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.rejected").value(3))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].row").value(3))
                .andExpect(jsonPath("$.errors[1].row").value(4))
                .andExpect(jsonPath("$.errors[2].row").value(5));

        mockMvc.perform(get("/api/v1/telematics/devices/" + deviceId).header("Authorization", adminToken))
                .andExpect(jsonPath("$.gpsStatus").value("ONLINE"))
                .andExpect(jsonPath("$.lastSpeedKph").value(42.0))
                .andExpect(jsonPath("$.lastOdometerKm").value(15250))
                .andExpect(jsonPath("$.minutesSinceLastCommunication").value(greaterThanOrEqualTo(4)));

        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.odometerKm").value(15250));
        // the journal entry is stamped with the sample time, so it sorts after the creation entry stamped "now"
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id() + "/odometer").header("Authorization", adminToken))
                .andExpect(jsonPath("$.content[?(@.source == 'TELEMATICS')].readingKm").value(hasItem(15250)));

        mockMvc.perform(get("/api/v1/telematics/latest").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.vehicleId == " + vehicle.id() + ")].speedKph").value(hasItem(42.0)))
                .andExpect(jsonPath("$[?(@.vehicleId == " + vehicle.id() + ")].gpsStatus").value(hasItem("ONLINE")))
                .andExpect(jsonPath("$[?(@.vehicleId == " + vehicle.id() + ")].plateNumber").value(hasItem(vehicle.plateNumber())));

        mockMvc.perform(get("/api/v1/telematics/vehicles/" + vehicle.id() + "/positions").header("Authorization", adminToken)
                        .param("from", now.minus(Duration.ofHours(1)).toString()).param("to", now.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.positions[0].speedKph").value(35.0))
                .andExpect(jsonPath("$.positions[1].speedKph").value(42.0));

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", adminToken).param("entityType", "VehiclePosition"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GPS status refresh marks stale devices OFFLINE, recent ones ONLINE, silent ones NO_SIGNAL and notifies fleet managers")
    void gpsStatusRefresh() throws Exception {
        String fleetManager = tokenFor(Roles.FLEET_MANAGER);
        VehicleResponse stale = data.vehicle();
        VehicleResponse fresh = data.vehicle();
        VehicleResponse silent = data.vehicle();
        long staleDevice = registerDevice(stale.id(), "UNIT-" + SEQ.incrementAndGet());
        long freshDevice = registerDevice(fresh.id(), "UNIT-" + SEQ.incrementAndGet());
        long silentDevice = registerDevice(silent.id(), "UNIT-" + SEQ.incrementAndGet());
        Instant now = Instant.now(clock);
        List<PositionInput> rows = List.of(
                position(stale.id(), null, null, now.minus(Duration.ofHours(5)), -1.95, 30.06, 0, null),
                position(fresh.id(), null, null, now.minus(Duration.ofMinutes(1)), -1.95, 30.06, 20, null));
        mockMvc.perform(post("/api/v1/telematics/positions").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(rows)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        mockMvc.perform(post("/api/v1/telematics/devices/refresh-status").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices").value(greaterThanOrEqualTo(3)));

        mockMvc.perform(get("/api/v1/telematics/devices/" + staleDevice).header("Authorization", adminToken))
                .andExpect(jsonPath("$.gpsStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.gpsProblem").value(true));
        mockMvc.perform(get("/api/v1/telematics/devices/" + freshDevice).header("Authorization", adminToken))
                .andExpect(jsonPath("$.gpsStatus").value("ONLINE"))
                .andExpect(jsonPath("$.healthy").value(true));
        mockMvc.perform(get("/api/v1/telematics/devices/" + silentDevice).header("Authorization", adminToken))
                .andExpect(jsonPath("$.gpsStatus").value("NO_SIGNAL"));

        mockMvc.perform(get("/api/v1/telematics/devices/problems").header("Authorization", adminToken))
                .andExpect(jsonPath("$.gpsProblems[*].id").value(hasItem((int) staleDevice)))
                .andExpect(jsonPath("$.gpsProblems[*].id").value(hasItem((int) silentDevice)));
        mockMvc.perform(get("/api/v1/telematics/devices").header("Authorization", adminToken).param("gpsStatus", "OFFLINE"))
                .andExpect(jsonPath("$[*].id").value(hasItem((int) staleDevice)));

        // the OFFLINE transition published a GPS_OFFLINE event -> in-app notification for fleet managers
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", fleetManager).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.type == 'GPS_OFFLINE')].entityId").value(hasItem(stale.id().intValue())));

        mockMvc.perform(post("/api/v1/telematics/devices/" + silentDevice + "/deactivate").header("Authorization", adminToken)
                        .param("reason", "Unit removed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.gpsStatus").value("DISCONNECTED"));
    }

    @Test
    @DisplayName("CSV import accepts the documented columns and reports unparsable rows without importing them")
    void csvImport() throws Exception {
        VehicleResponse vehicle = data.vehicle();
        Instant now = Instant.now(clock);
        String csv = "plate,recordedAt,latitude,longitude,speedKph,odometerKm,ignition\n"
                + vehicle.plateNumber() + "," + now.minus(Duration.ofMinutes(30)) + ",-1.9441,30.0619,55.5,15100,true\n"
                + vehicle.plateNumber() + "," + now.minus(Duration.ofMinutes(25)) + ",abc,30.0619,55.5,15105,true\n"
                + "ZZZ 001 X," + now.minus(Duration.ofMinutes(20)) + ",-1.9441,30.0619,10,,0\n"
                + vehicle.plateNumber() + ",not-a-date,-1.9441,30.0619,10,,0\n"
                + vehicle.plateNumber() + "," + now.minus(Duration.ofMinutes(10)) + ",-1.9500,30.0700,0,15130,false\n";
        MockMultipartFile file = new MockMultipartFile("file", "positions.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/telematics/positions/import").file(file).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(5))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.rejected").value(3))
                .andExpect(jsonPath("$.errors[0].row").value(2))
                .andExpect(jsonPath("$.errors[1].row").value(3))
                .andExpect(jsonPath("$.errors[2].row").value(4));

        mockMvc.perform(get("/api/v1/telematics/vehicles/" + vehicle.id() + "/positions").header("Authorization", adminToken))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.positions[0].source").value("csv"))
                .andExpect(jsonPath("$.positions[0].odometerKm").value(15100.0));
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.odometerKm").value(15130));

        MockMultipartFile noHeader = new MockMultipartFile("file", "bad.csv", "text/csv", "foo,bar\n1,2\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/telematics/positions/import").file(noHeader).header("Authorization", adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CSV_MISSING_COLUMN"));
    }

    @Test
    @DisplayName("TELEMATICS_READ can list devices but cannot register or ingest")
    void authorization() throws Exception {
        String viewer = tokenFor(Roles.VIEWER);
        mockMvc.perform(get("/api/v1/telematics/devices").header("Authorization", viewer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/telematics/latest").header("Authorization", viewer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/telematics/devices").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(deviceRequest(data.vehicle().id(), null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/telematics/positions").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());
    }
}
