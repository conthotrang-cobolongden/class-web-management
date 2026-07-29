package com.classmanagement.students;

import com.classmanagement.students.dto.StudentImportResponse;
import com.classmanagement.user.User;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/classes/{classId}/students")
public class StudentImportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final StudentImportService importService;

    public StudentImportController(StudentImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template(@PathVariable Long classId,
                                           @AuthenticationPrincipal User me) {
        byte[] workbook = importService.createTemplate(me, classId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("mau-danh-sach-hoc-sinh-lop-" + classId + ".xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(workbook);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudentImportResponse> importStudents(
            @PathVariable Long classId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User me) {
        StudentImportResponse result = importService.importWorkbook(me, classId, file);
        return ResponseEntity.status(result.hasErrors() ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED)
                .body(result);
    }
}
