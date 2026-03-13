package com.team_E_commerce.core.claim.dto;

import com.team_E_commerce.common.utils.MaskingUtils;
import com.team_E_commerce.core.claim.domain.Claim;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ClaimResponse(
        Long claimId,
        Long orderLineItemId,
        String productName,
        Claim.ClaimType claimType,
        Claim.ClaimStatus claimStatus,
        String reason,
        Integer claimAmount,
        Integer claimQuantity,
        List<String> imageUrls,
        RefundAccountResponse refundAccount,
        LocalDateTime createdAt
) {
    // 엔티티를 받아서 DTO로 변환하는 메서드

    public static ClaimResponse from(Claim claim) {
        return ClaimResponse.builder()
                .claimId(claim.getId())
                .orderLineItemId(claim.getOrderLineItemId())
                .productName(claim.getProductName())
                .claimType(claim.getClaimType())
                .claimStatus(claim.getClaimStatus())
                .reason(claim.getReason())
                .claimAmount(Math.toIntExact(claim.getClaimAmount()))
                .claimQuantity(Math.toIntExact(claim.getClaimQuantity()))
                .imageUrls(claim.getImageUrls())
                .refundAccount(RefundAccountResponse.from(claim.getRefundAccount()))
                .createdAt(claim.getCreatedAt())
                .build();
    }

    // 환불 계좌 전용 내부 Record
    public record RefundAccountResponse(
            String bankName,
            String accountNumber, // ★ 마스킹된 상태로 전달됨
            String accountHolder
    ) {
        public static RefundAccountResponse from(Claim.RefundAccount account) {
            if (account == null) return null;

            return new RefundAccountResponse(
                    account.getBankName(),
                    MaskingUtils.maskAccountNumber(account.getAccountNumber()), // 분리된 유틸리티 호출
                    account.getAccountHolder()
            );
        }
    }
}