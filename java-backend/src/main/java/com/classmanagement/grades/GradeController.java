package com.classmanagement.grades;

import com.classmanagement.common.MessageResponse;
import com.classmanagement.grades.dto.CreateGradeColumnRequest;
import com.classmanagement.grades.dto.GradeColumnDto;
import com.classmanagement.grades.dto.GradeMatrixResponse;
import com.classmanagement.grades.dto.SaveGradesRequest;
import com.classmanagement.grades.dto.UpdateGradeColumnRequest;
import com.classmanagement.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Quan ly diem (FR-5) — quyen theo lop check trong GradeService (NFR-2)
@RestController
@RequestMapping("/api")
public class GradeController {

    public record ColumnsResponse(List<GradeColumnDto> items) {
    }

    public record ColumnResponse(GradeColumnDto column) {
    }

    public record SaveGradesResponse(String message, int saved) {
    }

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    // ---------- Cot diem ----------

    @GetMapping("/classes/{id}/grade-columns")
    public ColumnsResponse listColumns(@PathVariable Long id, @AuthenticationPrincipal User me) {
        return new ColumnsResponse(gradeService.listColumns(me, id));
    }

    @PostMapping("/classes/{id}/grade-columns")
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnResponse createColumn(@PathVariable Long id,
                                       @Valid @RequestBody CreateGradeColumnRequest req,
                                       @AuthenticationPrincipal User me) {
        return new ColumnResponse(gradeService.createColumn(me, id, req));
    }

    @PutMapping("/grade-columns/{id}")
    public ColumnResponse updateColumn(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body,
                                       @AuthenticationPrincipal User me) {
        return new ColumnResponse(gradeService.updateColumn(me, id, new UpdateGradeColumnRequest(body)));
    }

    @DeleteMapping("/grade-columns/{id}")
    public MessageResponse deleteColumn(@PathVariable Long id, @AuthenticationPrincipal User me) {
        gradeService.deleteColumn(me, id);
        return new MessageResponse("Đã xóa cột điểm");
    }

    // ---------- Diem ----------

    @GetMapping("/classes/{id}/grades")
    public GradeMatrixResponse matrix(@PathVariable Long id, @AuthenticationPrincipal User me) {
        return gradeService.matrix(me, id);
    }

    @PutMapping("/grades")
    public SaveGradesResponse saveGrades(@RequestBody SaveGradesRequest req,
                                         @AuthenticationPrincipal User me) {
        return new SaveGradesResponse("Đã lưu điểm", gradeService.saveGrades(me, req));
    }

    // FR-5.4: CSV UTF-8 co BOM de Excel mo dung tieng Viet
    @GetMapping("/classes/{id}/grades/export")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long id, @AuthenticationPrincipal User me) {
        GradeService.CsvExport export = gradeService.exportCsv(me, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .body(export.content());
    }
}
