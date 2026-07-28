package com.classmanagement.classes;

import com.classmanagement.classes.dto.ClassDetailDto;
import com.classmanagement.classes.dto.ClassDto;
import com.classmanagement.classes.dto.ClassStudentDto;
import com.classmanagement.classes.dto.CreateClassRequest;
import com.classmanagement.classes.dto.ReplaceStaffRequest;
import com.classmanagement.classes.dto.ScheduleDto;
import com.classmanagement.classes.dto.ScheduleItemRequest;
import com.classmanagement.classes.dto.StaffDto;
import com.classmanagement.classes.dto.UpdateClassRequest;
import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.user.User;
import com.fasterxml.jackson.annotation.JsonProperty;
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

import java.util.List;
import java.util.Map;

// Module lop hoc (FR-2). Quyen check trong ClassService qua ClassAccessService (NFR-2):
// list = da dang nhap (loc theo lop minh), CRUD/staff = admin, schedules/generate = manager, con lai = member
@RestController
@RequestMapping("/api/classes")
public class ClassController {

    // "class" la keyword Java nen component dat ten khac + @JsonProperty de giu dung contract
    public record ClassResponse(@JsonProperty("class") ClassDetailDto schoolClass) {
    }

    public record StaffResponse(List<StaffDto> staff) {
    }

    public record SchedulesResponse(List<ScheduleDto> schedules) {
    }

    public record StudentsResponse(List<ClassStudentDto> items) {
    }

    public record GenerateSessionsResponse(int created, int skipped) {
    }

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping
    public PageResponse<ClassDto> list(@RequestParam(required = false) String page,
                                       @RequestParam(name = "page_size", required = false) String pageSize,
                                       @RequestParam(required = false) Integer grade,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(name = "school_year", required = false) String schoolYear,
                                       @AuthenticationPrincipal User me) {
        return classService.list(me, Pagination.of(page, pageSize), grade, status, schoolYear);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassResponse create(@Valid @RequestBody CreateClassRequest req,
                                @AuthenticationPrincipal User me) {
        return new ClassResponse(classService.create(me, req));
    }

    @GetMapping("/{id}")
    public ClassResponse get(@PathVariable Long id, @AuthenticationPrincipal User me) {
        return new ClassResponse(classService.get(me, id));
    }

    @PutMapping("/{id}")
    public ClassResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                @AuthenticationPrincipal User me) {
        return new ClassResponse(classService.update(me, id, new UpdateClassRequest(body)));
    }

    @PutMapping("/{id}/staff")
    public StaffResponse replaceStaff(@PathVariable Long id, @Valid @RequestBody ReplaceStaffRequest req,
                                      @AuthenticationPrincipal User me) {
        return new StaffResponse(classService.replaceStaff(me, id, req));
    }

    @PutMapping("/{id}/schedules")
    public SchedulesResponse replaceSchedules(@PathVariable Long id,
                                              @RequestBody List<ScheduleItemRequest> items,
                                              @AuthenticationPrincipal User me) {
        return new SchedulesResponse(classService.replaceSchedules(me, id, items));
    }

    @PostMapping("/{id}/generate-sessions")
    public GenerateSessionsResponse generateSessions(@PathVariable Long id,
                                                     @AuthenticationPrincipal User me) {
        ClassService.GenerateResult result = classService.generateSessions(me, id);
        return new GenerateSessionsResponse(result.created(), result.skipped());
    }

    @GetMapping("/{id}/students")
    public StudentsResponse students(@PathVariable Long id, @AuthenticationPrincipal User me) {
        return new StudentsResponse(classService.listStudents(me, id));
    }
}
