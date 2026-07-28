package com.classmanagement.attendance;

import com.classmanagement.attendance.dto.AttendanceRosterResponse;
import com.classmanagement.attendance.dto.AttendanceStatsItem;
import com.classmanagement.attendance.dto.SaveAttendanceRequest;
import com.classmanagement.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// FR-4: diem danh theo buoi + thong ke chuyen can. Quyen theo lop chan trong AttendanceService (NFR-2).
@RestController
@RequestMapping("/api")
public class AttendanceController {

    public record SaveAttendanceResponse(String message, int saved) {
    }

    public record StatsResponse(List<AttendanceStatsItem> items) {
    }

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/sessions/{id}/attendance")
    public AttendanceRosterResponse roster(@PathVariable Long id, @AuthenticationPrincipal User me) {
        return attendanceService.roster(me, id);
    }

    @PutMapping("/sessions/{id}/attendance")
    public SaveAttendanceResponse save(@PathVariable Long id,
                                       @RequestBody(required = false) SaveAttendanceRequest req,
                                       @AuthenticationPrincipal User me) {
        int saved = attendanceService.save(me, id, req);
        return new SaveAttendanceResponse("Đã lưu điểm danh", saved);
    }

    @GetMapping("/attendance/stats")
    public StatsResponse stats(@RequestParam(name = "class_id", required = false) Long classId,
                               @RequestParam(required = false) String from,
                               @RequestParam(required = false) String to,
                               @AuthenticationPrincipal User me) {
        return new StatsResponse(attendanceService.stats(me, classId, from, to));
    }
}
