package com.team_E_commerce.core.claim.domain;

import com.team_E_commerce.common.entity.BaseEntity;
import com.team_E_commerce.common.exception.BusinessException;
import com.team_E_commerce.common.exception.ErrorCode;
import com.team_E_commerce.core.claim.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "claims", indexes = {
        @Index(name = "idx_claim_member_id", columnList = "memberId"),
        @Index(name = "idx_claim_created_at", columnList = "createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Claim extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderLineItemId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String productName; // 프론트엔드 노출용 스냅샷

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus claimStatus;

    @Column(length = 500)
    private String reason;

    private Integer claimAmount;
    private Integer claimQuantity;

    // ★ 컬렉션 저장 맹점 해결: List를 JSON 텍스트로 변환하여 하나의 컬럼에 저장
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> imageUrls = new ArrayList<>();

    // ★ 값 타입(VO) 캡슐화: DB에는 3개의 컬럼으로 풀려서 저장됨
    @Embedded
    private RefundAccount refundAccount;

    public enum ClaimType { CANCEL, RETURN, EXCHANGE }
    public enum ClaimStatus { REQUESTED, PROCESSING, COMPLETED, REJECTED, WITHDRAWN }

    @Builder
    public Claim(Long orderLineItemId, Long memberId, String productName, ClaimType claimType,
                 String reason, Integer claimAmount, Integer claimQuantity,
                 List<String> imageUrls, RefundAccount refundAccount) {
        this.orderLineItemId = orderLineItemId;
        this.memberId = memberId;
        this.productName = productName;
        this.claimType = claimType;
        this.claimStatus = ClaimStatus.REQUESTED;
        this.reason = reason;
        this.claimAmount = claimAmount;
        this.claimQuantity = claimQuantity;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.refundAccount = refundAccount;
    }

    public void withdraw() {
        if (this.claimStatus != ClaimStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_CLAIM_STATUS);
        }
        this.claimStatus = ClaimStatus.WITHDRAWN;
    }

    public void startProcessing() {
        if (this.claimStatus != ClaimStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_CLAIM_STATUS);
        }
        this.claimStatus = ClaimStatus.PROCESSING;
    }

    public void complete() {
        if (this.claimStatus != ClaimStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_CLAIM_STATUS);
        }
        this.claimStatus = ClaimStatus.COMPLETED;
    }

    public void reject() {
        if (this.claimStatus == ClaimStatus.COMPLETED || this.claimStatus == ClaimStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.INVALID_CLAIM_STATUS);
        }
        this.claimStatus = ClaimStatus.REJECTED;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Embeddable
    public static class RefundAccount {
        @Column(name = "refund_bank_name", length = 50)
        private String bankName;

        @Column(name = "refund_account_number", length = 100)
        private String accountNumber;

        @Column(name = "refund_account_holder", length = 50)
        private String accountHolder;

        @Builder
        public RefundAccount(String bankName, String accountNumber, String accountHolder) {
            this.bankName = bankName;
            this.accountNumber = accountNumber;
            this.accountHolder = accountHolder;
        }
    }
}