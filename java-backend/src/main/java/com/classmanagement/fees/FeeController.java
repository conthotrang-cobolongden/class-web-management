package com.classmanagement.fees;

import com.classmanagement.common.PageResponse;
import com.classmanagement.common.Pagination;
import com.classmanagement.fees.dto.AdjustFeeRequest;
import com.classmanagement.fees.dto.CreatePaymentRequest;
import com.classmanagement.fees.dto.CreatePaymentResponse;
import com.classmanagement.fees.dto.FeeDto;
import com.classmanagement.fees.dto.GenerateFeesRequest;
import com.classmanagement.fees.dto.GenerateFeesResponse;
import com.classmanagement.fees.dto.PaymentDto;
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

import java.util.List;

// Hoc phi (FR-6) — quyen theo lop chan trong FeeService qua ClassAccessService (NFR-2)
@RestController
@RequestMapping("/api/fees")
public class FeeController {

    public record FeeResponse(FeeDto fee) {
    }

    public record PaymentItemsResponse(List<PaymentDto> items) {
    }

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    @PostMapping("/generate")
    public GenerateFeesResponse generate(@Valid @RequestBody GenerateFeesRequest req,
                                         @AuthenticationPrincipal User me) {
        return feeService.generate(me, req);
    }

    @GetMapping
    public PageResponse<FeeDto> list(@RequestParam(required = false) String page,
                                     @RequestParam(name = "page_size", required = false) String pageSize,
                                     @RequestParam(name = "class_id", required = false) Long classId,
                                     @RequestParam(required = false) Integer year,
                                     @RequestParam(required = false) Integer month,
                                     @RequestParam(required = false) String status,
                                     @AuthenticationPrincipal User me) {
        return feeService.list(me, Pagination.of(page, pageSize), classId, year, month, status);
    }

    @PutMapping("/{id}/adjust")
    public FeeResponse adjust(@PathVariable Long id, @RequestBody AdjustFeeRequest req,
                              @AuthenticationPrincipal User me) {
        return new FeeResponse(feeService.adjust(me, id, req));
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePaymentResponse addPayment(@PathVariable Long id,
                                            @Valid @RequestBody CreatePaymentRequest req,
                                            @AuthenticationPrincipal User me) {
        return feeService.addPayment(me, id, req);
    }

    @GetMapping("/{id}/payments")
    public PaymentItemsResponse listPayments(@PathVariable Long id, @AuthenticationPrincipal User me) {
        return new PaymentItemsResponse(feeService.listPayments(me, id));
    }
}
