package com.moviebooking.refund;

import com.moviebooking.refund.dto.RefundDtos.RefundPolicyRequest;
import com.moviebooking.refund.dto.RefundDtos.RefundPolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/refund-policies")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Refund Policies", description = "Configurable time-based cancellation refund rules")
public class AdminRefundPolicyController {

    private final RefundPolicyService refundPolicyService;

    @Operation(summary = "Create a refund policy (optionally as the system default)")
    @PostMapping
    public ResponseEntity<RefundPolicyResponse> create(@Valid @RequestBody RefundPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(refundPolicyService.create(request));
    }

    @Operation(summary = "Update a refund policy's rules or promote it to default")
    @PutMapping("/{id}")
    public RefundPolicyResponse update(@PathVariable Long id, @Valid @RequestBody RefundPolicyRequest request) {
        return refundPolicyService.update(id, request);
    }

    @Operation(summary = "List all refund policies")
    @GetMapping
    public List<RefundPolicyResponse> list() {
        return refundPolicyService.list();
    }
}
