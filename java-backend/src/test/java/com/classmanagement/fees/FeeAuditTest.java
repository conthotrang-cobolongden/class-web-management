package com.classmanagement.fees;

import com.classmanagement.TestUsers;
import com.classmanagement.common.AuditService;
import com.classmanagement.common.ClassAccessService;
import com.classmanagement.common.FeeStatusService;
import com.classmanagement.domain.AuditLog;
import com.classmanagement.domain.AuditLogRepository;
import com.classmanagement.domain.EnrollmentRepository;
import com.classmanagement.domain.MonthlyFee;
import com.classmanagement.domain.MonthlyFeeRepository;
import com.classmanagement.domain.Payment;
import com.classmanagement.domain.PaymentRepository;
import com.classmanagement.domain.SchoolClassRepository;
import com.classmanagement.domain.StudentRepository;
import com.classmanagement.fees.dto.CreatePaymentRequest;
import com.classmanagement.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeeAuditTest {

    private final MonthlyFeeRepository monthlyFees = mock(MonthlyFeeRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ClassAccessService access = mock(ClassAccessService.class);
    private final FeeStatusService feeStatus = mock(FeeStatusService.class);
    private final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
    private FeeService service;

    @BeforeEach
    void setUp() {
        service = new FeeService(monthlyFees, payments, mock(EnrollmentRepository.class),
                mock(StudentRepository.class), mock(SchoolClassRepository.class), mock(UserRepository.class),
                access, feeStatus, new AuditService(auditLogs, new ObjectMapper().findAndRegisterModules()));
        when(monthlyFees.findById(100L)).thenReturn(Optional.of(monthlyFee()));
        when(feeStatus.refreshFeeStatus(any(MonthlyFee.class))).thenReturn("partial");
        doAnswer(invocation -> {
            Payment saved = invocation.getArgument(0, Payment.class);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        }).when(payments).save(any(Payment.class));
    }

    @Test
    void successfulPaymentWritesCategorizedAuditWithFeeContext() throws Exception {
        service.addPayment(TestUsers.assistant(2L), 100L, request(new BigDecimal("500000")));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("fees", log.getCategory());
        assertEquals("create", log.getAction());
        assertEquals(42L, log.getRecordId());
        assertEquals(2L, log.getUserId());
        assertTrue(log.getNewValues().contains("\"monthly_fee_id\":100"));
        assertTrue(log.getNewValues().contains("\"student_id\":7"));
        assertTrue(log.getNewValues().contains("\"class_id\":10"));
        assertEquals(0, new ObjectMapper().readTree(log.getNewValues()).get("amount")
                .decimalValue().compareTo(new BigDecimal("500000")));
        assertTrue(log.getNewValues().contains("\"method\":\"cash\""));
    }

    @Test
    void rejectedPaymentDoesNotWriteSuccessAudit() {
        assertThrows(RuntimeException.class, () -> service.addPayment(
                TestUsers.assistant(2L), 100L, request(BigDecimal.ZERO)));

        verify(auditLogs, never()).save(any(AuditLog.class));
    }

    private static CreatePaymentRequest request(BigDecimal amount) {
        return new CreatePaymentRequest(amount, "cash", Instant.parse("2026-07-28T12:00:00Z"), null);
    }

    private static MonthlyFee monthlyFee() {
        MonthlyFee fee = new MonthlyFee();
        ReflectionTestUtils.setField(fee, "id", 100L);
        fee.setStudentId(7L);
        fee.setClassId(10L);
        fee.setFeeYear(2026);
        fee.setFeeMonth(7);
        fee.setBaseAmount(new BigDecimal("1000000"));
        fee.setStatus("unpaid");
        return fee;
    }
}
