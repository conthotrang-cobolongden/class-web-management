package com.classmanagement.students;

import com.classmanagement.common.MessageResponse;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.students.dto.CreateStudentRequest;
import com.classmanagement.students.dto.EnrollRequest;
import com.classmanagement.students.dto.StudentDetailDto;
import com.classmanagement.students.dto.StudentDto;
import com.classmanagement.students.dto.TransferRequest;
import com.classmanagement.students.dto.UpdateStudentRequest;
import com.classmanagement.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// FR-3: ho so hoc sinh — moi user dang nhap deu vao duoc, quyen theo lop chan trong service (NFR-2)
@RestController
@RequestMapping("/api/students")
public class StudentController {

    public record StudentResponse(StudentDto student) {
    }

    public record StudentDetailResponse(StudentDetailDto student) {
    }

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    public PageResponse<StudentDto> list(@RequestParam(required = false) String page,
                                         @RequestParam(name = "page_size", required = false) String pageSize,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(name = "class_id", required = false) Long classId,
                                         @RequestParam(required = false) Integer grade,
                                         @RequestParam(required = false) String status,
                                         @AuthenticationPrincipal User me) {
        return studentService.list(me, Pagination.of(page, pageSize), q, classId, grade, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse create(@Valid @RequestBody CreateStudentRequest req,
                                  @AuthenticationPrincipal User me) {
        return new StudentResponse(studentService.create(me, req));
    }

    @GetMapping("/{id}")
    public StudentDetailResponse detail(@PathVariable Long id, @AuthenticationPrincipal User me) {
        return new StudentDetailResponse(studentService.detail(me, id));
    }

    @PutMapping("/{id}")
    public StudentResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                  @AuthenticationPrincipal User me) {
        return new StudentResponse(studentService.update(me, id, new UpdateStudentRequest(body)));
    }

    @PostMapping("/{id}/transfer")
    public MessageResponse transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest req,
                                    @AuthenticationPrincipal User me) {
        studentService.transfer(me, id, req);
        return new MessageResponse("Đã chuyển lớp");
    }

    @PostMapping("/{id}/enroll")
    public MessageResponse enroll(@PathVariable Long id, @Valid @RequestBody EnrollRequest req,
                                  @AuthenticationPrincipal User me) {
        studentService.enroll(me, id, req);
        return new MessageResponse("Đã gán học sinh vào lớp");
    }

    @PutMapping("/{id}/custom-fee")
    public MessageResponse customFee(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     @AuthenticationPrincipal User me) {
        studentService.updateCustomFee(me, id, body);
        return new MessageResponse("Đã cập nhật học phí riêng");
    }
}
