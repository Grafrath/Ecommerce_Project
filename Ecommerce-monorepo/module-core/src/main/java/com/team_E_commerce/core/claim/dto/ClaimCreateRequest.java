package com.team_E_commerce.core.claim.dto;

import com.team_E_commerce.core.claim.domain.Claim;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ClaimCreateRequest(
        @NotNull(message = "클레임 대상 상품은 필수입니다.")
        @Size(min = 1, message = "최소 1개 이상의 상품을 선택해야 합니다.")
        List<ClaimItem> claimItems,

        @NotNull(message = "클레임 타입(CANCEL, RETURN, EXCHANGE)은 필수입니다.")
        Claim.ClaimType claimType, // ★ String -> Enum으로 변경 (오타 원천 차단)

        @NotBlank(message = "사유를 입력해 주세요.")
        String reason,

        List<String> imageUrls,

        RefundAccountDto refundAccount
) {
    public record ClaimItem(
            @NotNull Long orderLineItemId,
            @NotNull @Min(1) Integer quantity
    ) {}

    public record RefundAccountDto(
            String bankName,
            String accountNumber,
            String accountHolder
    ) {}
}