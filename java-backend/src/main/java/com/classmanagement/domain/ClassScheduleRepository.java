package com.classmanagement.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, Long> {

    List<ClassSchedule> findByClassIdOrderByDayOfWeekAscStartTimeAsc(Long classId);

    // PUT /api/classes/{id}/schedules — thay toan bo khung tuan (goi trong @Transactional)
    void deleteByClassId(Long classId);
}
