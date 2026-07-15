package com.moviebooking.discount;

import com.moviebooking.discount.dto.DiscountDtos.DiscountCodeRequest;
import com.moviebooking.discount.dto.DiscountDtos.DiscountCodeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/discounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Discounts", description = "Manage discount codes")
public class AdminDiscountController {

    private final DiscountService discountService;

    @Operation(summary = "Create a discount code")
    @PostMapping
    public ResponseEntity<DiscountCodeResponse> create(@Valid @RequestBody DiscountCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(discountService.create(request));
    }

    @Operation(summary = "Update a discount code")
    @PutMapping("/{id}")
    public DiscountCodeResponse update(@PathVariable Long id, @Valid @RequestBody DiscountCodeRequest request) {
        return discountService.update(id, request);
    }

    @Operation(summary = "Deactivate a discount code")
    @DeleteMapping("/{id}")
    public DiscountCodeResponse deactivate(@PathVariable Long id) {
        return discountService.deactivate(id);
    }

    @Operation(summary = "List all discount codes")
    @GetMapping
    public List<DiscountCodeResponse> list() {
        return discountService.list();
    }
}
