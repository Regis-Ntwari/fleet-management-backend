package com.limoz.fleet.telematics.movement;

import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.support.AbstractIntegrationTest;
import com.limoz.fleet.support.TestData;
import com.limoz.fleet.telematics.dto.PositionInput;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyMovementIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(500);

    @Autowired
    TestData data;

    @Autowired
    Clock clock;

    @Autowired
    ZoneId operationalZone;

    @Autowired
    JdbcClient jdbc;

    private LocalDate yesterday() {
        return LocalDate.now(clock).minusDays(1);
    }

    private Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(operationalZone).toInstant();
    }

    private void ingest(List<PositionInput> rows) throws Exception {
        mockMvc.perform(post("/api/v1/telematics/positions").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(rows)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected").value(0));
    }

    private static PositionInput position(Long vehicleId, Instant at, double lat, double lon, double speed, Double odometer) {
        return new PositionInput(vehicleId, null, null, at, BigDecimal.valueOf(lat), BigDecimal.valueOf(lon), BigDecimal.valueOf(speed),
                null, odometer == null ? null : BigDecimal.valueOf(odometer), speed > 0, null, null);
    }

    @Test
    @DisplayName("positions of a day are turned into a TELEMATICS summary with distance, driving time and flags")
    void summaryFromPositions() throws Exception {
        LocalDate day = yesterday();
        VehicleResponse steady = data.vehicle();
        VehicleResponse nightRunner = data.vehicle();

        // 08:00 -> 09:00 every 5 minutes at 60 km/h, odometer 15000 -> 15060
        List<PositionInput> rows = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            double speed = i == 12 ? 0 : 60;
            rows.add(position(steady.id(), at(day, 8, 0).plusSeconds(i * 300L), -1.94 - i * 0.004, 30.06, speed, 15000.0 + i * 5));
        }
        // 23:00 -> 23:30 every 5 minutes at 90 km/h: night driving and over-speeding
        for (int i = 0; i <= 6; i++) {
            rows.add(position(nightRunner.id(), at(day, 23, 0).plusSeconds(i * 300L), -1.94 - i * 0.006, 30.06, 90, null));
        }
        ingest(rows);

        mockMvc.perform(post("/api/v1/movement/recompute").header("Authorization", adminToken).param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(day.toString()))
                .andExpect(jsonPath("$.withPositions").value(greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/v1/movement/daily").header("Authorization", adminToken)
                        .param("date", day.toString()).param("vehicleId", steady.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].vehicle.plateNumber").value(steady.plateNumber()))
                .andExpect(jsonPath("$.content[0].distanceKm").value(60.0))
                .andExpect(jsonPath("$.content[0].moved").value(true))
                .andExpect(jsonPath("$.content[0].drivingMinutes").value(60))
                .andExpect(jsonPath("$.content[0].idleMinutes").value(0))
                .andExpect(jsonPath("$.content[0].nightDrivingMinutes").value(0))
                .andExpect(jsonPath("$.content[0].maxSpeedKph").value(60.0))
                .andExpect(jsonPath("$.content[0].tripsCount").value(0))
                .andExpect(jsonPath("$.content[0].dataSource").value("TELEMATICS"))
                .andExpect(jsonPath("$.content[0].flags", hasSize(0)))
                .andExpect(jsonPath("$.content[0].firstMovementAt").value(at(day, 8, 0).toString()))
                .andExpect(jsonPath("$.content[0].lastMovementAt").value(at(day, 8, 55).toString()));

        mockMvc.perform(get("/api/v1/movement/daily").header("Authorization", adminToken)
                        .param("date", day.toString()).param("vehicleId", nightRunner.id().toString()))
                .andExpect(jsonPath("$.content[0].drivingMinutes").value(30))
                .andExpect(jsonPath("$.content[0].nightDrivingMinutes").value(30))
                .andExpect(jsonPath("$.content[0].flags").value(containsInAnyOrder("NIGHT_DRIVING", "OVER_SPEEDING")));

        // JSONB flag filter
        mockMvc.perform(get("/api/v1/movement/daily").header("Authorization", adminToken)
                        .param("date", day.toString()).param("flag", "OVER_SPEEDING").param("vehicleId", nightRunner.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/movement/daily").header("Authorization", adminToken)
                        .param("date", day.toString()).param("flag", "OVER_SPEEDING").param("vehicleId", steady.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/vehicles/" + steady.id() + "/movement").header("Authorization", adminToken)
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].summaryDate").value(day.toString()));

        mockMvc.perform(get("/api/v1/movement/summary").header("Authorization", adminToken).param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehiclesMoved").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.flaggedCounts.OVER_SPEEDING").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.flaggedCounts.NIGHT_DRIVING").value(greaterThanOrEqualTo(1)));

        // recomputing is idempotent (upsert on vehicle + date)
        mockMvc.perform(post("/api/v1/movement/recompute").header("Authorization", adminToken).param("date", day.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/movement/daily").header("Authorization", adminToken)
                        .param("date", day.toString()).param("vehicleId", steady.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("a vehicle without positions but with a trip that day gets a TRIPS summary; a future date is rejected")
    void summaryFromTrips() throws Exception {
        LocalDate day = yesterday();
        VehicleResponse vehicle = data.vehicle();
        DriverResponse driver = data.driver();
        jdbc.sql("insert into trips (trip_number, vehicle_id, driver_id, origin, destination, scheduled_start_at, started_at, ended_at, "
                        + "distance_km, duration_minutes, max_speed_kph, status) values (:number, :vehicleId, :driverId, 'Kigali', 'Huye', "
                        + ":start, :start, :end, 85.5, 120, 70, 'COMPLETED')")
                .param("number", "TRP-MV-" + SEQ.incrementAndGet())
                .param("vehicleId", vehicle.id())
                .param("driverId", driver.id())
                .param("start", OffsetDateTime.ofInstant(at(day, 10, 0), ZoneOffset.UTC))
                .param("end", OffsetDateTime.ofInstant(at(day, 12, 0), ZoneOffset.UTC))
                .update();

        mockMvc.perform(post("/api/v1/movement/recompute").header("Authorization", adminToken).param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withTripsOnly").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/movement/daily").header("Authorization", adminToken)
                        .param("date", day.toString()).param("vehicleId", vehicle.id().toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dataSource").value("TRIPS"))
                .andExpect(jsonPath("$.content[0].tripsCount").value(1))
                .andExpect(jsonPath("$.content[0].distanceKm").value(85.5))
                .andExpect(jsonPath("$.content[0].drivingMinutes").value(120))
                .andExpect(jsonPath("$.content[0].moved").value(true))
                .andExpect(jsonPath("$.content[0].firstMovementAt").value(at(day, 10, 0).toString()))
                .andExpect(jsonPath("$.content[0].lastMovementAt").value(at(day, 12, 0).toString()))
                .andExpect(jsonPath("$.content[0].flags", hasSize(0)));

        mockMvc.perform(post("/api/v1/movement/recompute").header("Authorization", adminToken)
                        .param("date", LocalDate.now(clock).plusDays(1).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));
    }

    @Test
    @DisplayName("a vehicle with neither positions nor trips is NOT_MOVED with data source NONE")
    void noData() throws Exception {
        LocalDate day = yesterday();
        VehicleResponse vehicle = data.vehicle();
        mockMvc.perform(post("/api/v1/movement/recompute").header("Authorization", adminToken).param("date", day.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/movement/daily").header("Authorization", adminToken)
                        .param("date", day.toString()).param("vehicleId", vehicle.id().toString()))
                .andExpect(jsonPath("$.content[0].dataSource").value("NONE"))
                .andExpect(jsonPath("$.content[0].moved").value(false))
                .andExpect(jsonPath("$.content[0].flags").value(containsInAnyOrder("NOT_MOVED")));
    }
}
