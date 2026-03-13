package com.team_E_commerce.core.claim.service;

import com.team_E_commerce.common.exception.BusinessException;
import com.team_E_commerce.common.exception.ErrorCode;
import com.team_E_commerce.core.claim.client.PaymentInternalClient;
import com.team_E_commerce.core.claim.client.SupportInternalClient;
import com.team_E_commerce.core.claim.client.dto.AdminClaimHistoryRequestDto;
import com.team_E_commerce.core.claim.domain.Claim;
import com.team_E_commerce.core.claim.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimAdminService {

    private final ClaimRepository claimRepository;
    private final PaymentInternalClient paymentInternalClient;
    private final SupportInternalClient supportInternalClient;


    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void approveClaim(Long adminId, Long claimId) {

        // 갱신 손실 방지
        Claim claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));

        // 변경되기 직전의 상태를 로컬 변수에 보관
        String previousStatus = claim.getClaimStatus().name();

        // 1. 상태 변경
        claim.complete();

        // 2. 결제 취소 요청
        paymentInternalClient.cancelPayment(claim.getOrderLineItemId(), claim.getClaimAmount());

        // 백오피스 이력 전송 (Retry 3회 실패 시 전체 롤백)
        AdminClaimHistoryRequestDto historyDto = new AdminClaimHistoryRequestDto(
                adminId, claim.getId(), claim.getOrderNumber(),
                previousStatus, claim.getClaimStatus().name(),
                "APPROVE", null
        );
        supportInternalClient.sendAdminHistory(historyDto);

        log.info("관리자 클레임 승인 완료 (결제 취소 및 이력 전송 성공) - claimId: {}, adminId: {}", claimId, adminId);
    }

    // 환불 거절
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void rejectClaim(Long adminId, Long claimId, String rejectReason) {

        Claim claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));

        String previousStatus = claim.getClaimStatus().name();

        // 1. 상태 변경 및 거절 사유 기록
        claim.reject(rejectReason);

        // 2. 백오피스 이력 전송 (실패 시 롤백)
        AdminClaimHistoryRequestDto historyDto = new AdminClaimHistoryRequestDto(
                adminId, claim.getId(), claim.getOrderNumber(),
                previousStatus, claim.getClaimStatus().name(),
                "REJECT", rejectReason
        );
        supportInternalClient.sendAdminHistory(historyDto);

        log.info("관리자 클레임 거절 완료 - claimId: {}, adminId: {}", claimId, adminId);
    }
}