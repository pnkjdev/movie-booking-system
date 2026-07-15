package com.moviebooking.refund;

import com.moviebooking.common.error.ApiException;
import com.moviebooking.common.error.ErrorCode;
import com.moviebooking.refund.dto.RefundDtos.RefundPolicyRequest;
import com.moviebooking.refund.dto.RefundDtos.RefundPolicyResponse;
import com.moviebooking.refund.dto.RefundDtos.RefundRuleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RefundPolicyService {

    private final RefundPolicyRepository refundPolicyRepository;

    @Transactional
    public RefundPolicyResponse create(RefundPolicyRequest request) {
        if (refundPolicyRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Refund policy already exists: " + request.name());
        }
        validateRules(request);
        RefundPolicy policy = RefundPolicy.builder()
                .name(request.name().trim())
                .description(request.description())
                .active(true)
                .defaultPolicy(request.defaultPolicy())
                .rules(toRules(request))
                .build();
        if (request.defaultPolicy()) {
            unsetOtherDefaults(null);
        }
        return RefundPolicyResponse.from(refundPolicyRepository.save(policy));
    }

    @Transactional
    public RefundPolicyResponse update(Long id, RefundPolicyRequest request) {
        RefundPolicy policy = refundPolicyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Refund policy not found: " + id));
        validateRules(request);
        policy.setName(request.name().trim());
        policy.setDescription(request.description());
        policy.getRules().clear();
        policy.getRules().addAll(toRules(request));
        if (request.defaultPolicy() && !policy.isDefaultPolicy()) {
            unsetOtherDefaults(id);
            policy.setDefaultPolicy(true);
        } else if (!request.defaultPolicy() && policy.isDefaultPolicy()) {
            throw ApiException.badRequest(
                    "Cannot unset the default policy directly; mark another policy as default instead");
        }
        return RefundPolicyResponse.from(policy);
    }

    @Transactional(readOnly = true)
    public List<RefundPolicyResponse> list() {
        return refundPolicyRepository.findAll().stream().map(RefundPolicyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<RefundPolicy> defaultPolicy() {
        return refundPolicyRepository.findByDefaultPolicyTrue();
    }

    private void unsetOtherDefaults(Long exceptId) {
        List<RefundPolicy> defaults = exceptId == null
                ? refundPolicyRepository.findAll().stream().filter(RefundPolicy::isDefaultPolicy).toList()
                : refundPolicyRepository.findByDefaultPolicyTrueAndIdNot(exceptId);
        defaults.forEach(policy -> policy.setDefaultPolicy(false));
    }

    private void validateRules(RefundPolicyRequest request) {
        Set<Integer> thresholds = new HashSet<>();
        for (RefundRuleDto rule : request.rules()) {
            if (!thresholds.add(rule.minHoursBeforeShow())) {
                throw ApiException.badRequest(
                        "Duplicate rule threshold: " + rule.minHoursBeforeShow() + " hours");
            }
        }
    }

    private List<RefundRule> toRules(RefundPolicyRequest request) {
        return request.rules().stream()
                .map(dto -> new RefundRule(dto.minHoursBeforeShow(), dto.refundPercent()))
                .toList();
    }
}
