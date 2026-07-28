package com.classmanagement.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MonthlyFeeRepository extends JpaRepository<MonthlyFee, Long> {

    // Idempotent khi sinh phi thang: unique (student_id, class_id, fee_year, fee_month)
    boolean existsByStudentIdAndClassIdAndFeeYearAndFeeMonth(Long studentId, Long classId,
                                                             int feeYear, int feeMonth);

    Optional<MonthlyFee> findByStudentIdAndClassIdAndFeeYearAndFeeMonth(Long studentId, Long classId,
                                                                        int feeYear, int feeMonth);

    List<MonthlyFee> findByClassIdAndFeeYearAndFeeMonth(Long classId, int feeYear, int feeMonth);

    // FR-7.3 doanh thu: admin xem toan trung tam / GV chinh xem cac lop minh
    List<MonthlyFee> findByFeeYearAndFeeMonth(int feeYear, int feeMonth);

    List<MonthlyFee> findByClassIdInAndFeeYearAndFeeMonth(Collection<Long> classIds, int feeYear, int feeMonth);

    // GET /api/fees — admin: moi filter deu optional
    @Query("""
            select f from MonthlyFee f
            where (:classId is null or f.classId = :classId)
              and (:year is null or f.feeYear = :year)
              and (:month is null or f.feeMonth = :month)
              and (:status is null or f.status = :status)
            """)
    Page<MonthlyFee> search(@Param("classId") Long classId,
                            @Param("year") Integer year,
                            @Param("month") Integer month,
                            @Param("status") String status,
                            Pageable pageable);

    // GET /api/fees — GV/TG: gioi han trong cac lop minh duoc gan
    @Query("""
            select f from MonthlyFee f
            where f.classId in :classIds
              and (:classId is null or f.classId = :classId)
              and (:year is null or f.feeYear = :year)
              and (:month is null or f.feeMonth = :month)
              and (:status is null or f.status = :status)
            """)
    Page<MonthlyFee> searchWithin(@Param("classIds") Collection<Long> classIds,
                                  @Param("classId") Long classId,
                                  @Param("year") Integer year,
                                  @Param("month") Integer month,
                                  @Param("status") String status,
                                  Pageable pageable);

    // FR-7.4 dashboard: khoan phi chua thu du trong thang (unpaid + partial -> StatusNot("paid"))
    long countByFeeYearAndFeeMonthAndStatus(int feeYear, int feeMonth, String status);

    long countByFeeYearAndFeeMonthAndStatusNot(int feeYear, int feeMonth, String status);
}
