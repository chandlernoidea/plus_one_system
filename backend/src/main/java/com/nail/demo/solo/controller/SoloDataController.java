package com.nail.demo.solo.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.ClientImportResponse;
import com.nail.demo.solo.dto.CsvImportRequest;
import com.nail.demo.solo.dto.DemoSeedResponse;
import com.nail.demo.solo.dto.ReminderBoardResponse;
import com.nail.demo.solo.service.ClientService;
import com.nail.demo.solo.service.DemoDataService;
import com.nail.demo.solo.service.ExportService;
import com.nail.demo.solo.service.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P7 的几件杂事：提醒、CSV 导入导出、一键演示数据。
 */
@RestController
@RequestMapping("/api/solo")
@RequiredArgsConstructor
public class SoloDataController {

    private final ReminderService reminderService;
    private final ClientService clientService;
    private final ExportService exportService;
    private final DemoDataService demoDataService;

    /** 今天要发的微信：昨日未确认 / 今日 / 明日。 */
    @GetMapping("/reminders")
    public ApiResponse<ReminderBoardResponse> reminders(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return ApiResponse.success(reminderService.board(date));
    }

    /** CSV 导入预览：解析 + 同名标黄，不入库。 */
    @PostMapping("/clients/import/preview")
    public ApiResponse<ClientImportResponse> previewImport(@Valid @RequestBody CsvImportRequest request) {
        return ApiResponse.success(clientService.previewImport(request.getCsv()));
    }

    /** 确认导入。 */
    @PostMapping("/clients/import")
    public ApiResponse<ClientImportResponse.Result> importClients(@Valid @RequestBody CsvImportRequest request) {
        return ApiResponse.success(clientService.importClients(request.getCsv()));
    }

    @GetMapping("/export/clients.csv")
    public ResponseEntity<byte[]> exportClients() {
        return csv(exportService.clientsCsv(), "solo-clients.csv");
    }

    @GetMapping("/export/bookings.csv")
    public ResponseEntity<byte[]> exportBookings() {
        return csv(exportService.bookingsCsv(), "solo-bookings.csv");
    }

    @GetMapping("/export/flows.csv")
    public ResponseEntity<byte[]> exportFlows() {
        return csv(exportService.flowsCsv(), "solo-flows.csv");
    }

    /** 一键演示数据：3 客户 / 5 款式 / 今天 4 单（含 1 单连做）。 */
    @PostMapping("/demo")
    public ApiResponse<DemoSeedResponse> demo() {
        return ApiResponse.success(demoDataService.seed());
    }

    private ResponseEntity<byte[]> csv(String content, String filename) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }
}
