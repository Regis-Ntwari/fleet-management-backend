package com.limoz.fleet.importer;

import com.limoz.fleet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("vehicle CSV import reports imported, duplicate and rejected rows without silently accepting bad data")
    void vehicleCsvImport() throws Exception {
        String csv = """
                Plate Number,Make,Model,Category,Year,Odometer Km,Fuel Type,Purchase Date
                RAD 601 I,Toyota,Hiace,MINIBUS,2021,120500,DIESEL,15/03/2021
                RAD 602 I,Toyota,Coaster,Coaster,2020,"98,000",diesel,2020-06-01
                RAD 601 I,Toyota,Hiace,MINIBUS,2021,120500,DIESEL,
                RAD 603 I,,Fuso,CARGO_VAN,2019,50000,DIESEL,
                RAD 604 I,Isuzu,NLR,DOES_NOT_EXIST,2019,50000,DIESEL,
                """;
        MockMultipartFile file = new MockMultipartFile("file", "vehicles.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/imports/vehicles").file(file).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(5))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.rejected").value(3))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[1].reason").value("make is required"))
                .andExpect(jsonPath("$.errors[2].reason").value("Unknown vehicle category 'DOES_NOT_EXIST'"));

        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", adminToken).param("q", "RAD 602 I"))
                .andExpect(jsonPath("$.content[0].odometerKm").value(98000))
                .andExpect(jsonPath("$.content[0].categoryName").value("Coaster"));

        // re-importing the same file: nothing new, both existing plates are duplicates
        mockMvc.perform(multipart("/api/v1/imports/vehicles").file(file).header("Authorization", adminToken))
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.duplicates").value(3));
    }

    @Test
    @DisplayName("driver import accepts full_name columns and flags duplicate licences")
    void driverImport() throws Exception {
        String csv = """
                full_name,licence_number,phone,license_expiry_date,employment_status
                HABIMANA Joseph,RW-DL-90001,0788123456,2028-01-31,Full time
                UWASE Alice,RW-DL-90002,0788123457,31/01/2028,CONTRACT
                MUGISHA Olivier,RW-DL-90001,0788123458,2028-01-31,
                """;
        MockMultipartFile file = new MockMultipartFile("file", "drivers.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/imports/drivers").file(file).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.duplicates").value(1));
        mockMvc.perform(get("/api/v1/drivers").header("Authorization", adminToken).param("q", "RW-DL-90002"))
                .andExpect(jsonPath("$.content[0].firstName").value("UWASE"))
                .andExpect(jsonPath("$.content[0].employmentStatus").value("CONTRACT"));
    }

    @Test
    @DisplayName("Excel workbooks are imported with numeric and date cells handled")
    void excelImport() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = wb.createSheet("Vehicles");
            String[] headers = {"Plate Number", "Make", "Model", "Category", "Year", "Odometer Km", "Purchase Date"};
            var h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            var r = sheet.createRow(1);
            r.createCell(0).setCellValue("RAD 701 X");
            r.createCell(1).setCellValue("Yutong");
            r.createCell(2).setCellValue("ZK6122");
            r.createCell(3).setCellValue("COACH");
            r.createCell(4).setCellValue(2023);
            r.createCell(5).setCellValue(45200);
            var dateCell = r.createCell(6);
            dateCell.setCellValue(java.time.LocalDate.of(2023, 2, 10));
            var style = wb.createCellStyle();
            style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            dateCell.setCellStyle(style);
            wb.write(out);
        }
        MockMultipartFile file = new MockMultipartFile("file", "vehicles.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        mockMvc.perform(multipart("/api/v1/imports/vehicles").file(file).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.rejected").value(0));
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", adminToken).param("q", "RAD 701 X"))
                .andExpect(jsonPath("$.content[0].modelYear").value(2023))
                .andExpect(jsonPath("$.content[0].odometerKm").value(45200))
                .andExpect(jsonPath("$.content[0].purchaseDate").value("2023-02-10"));
    }

    @Test
    @DisplayName("import endpoints require IMPORT_DATA")
    void requiresPermission() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "v.csv", "text/csv", "plate_number\n".getBytes());
        mockMvc.perform(multipart("/api/v1/imports/vehicles").file(file).header("Authorization", tokenFor("VIEWER")))
                .andExpect(status().isForbidden());
    }
}
