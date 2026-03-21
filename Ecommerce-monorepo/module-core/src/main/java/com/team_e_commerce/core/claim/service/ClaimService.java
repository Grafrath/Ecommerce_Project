package com.team_e_commerce.core.claim.service;

import com.team_e_commerce.common.exception.BusinessException;
import com.team_e_commerce.common.exception.ErrorCode;
import com.team_e_commerce.common.dto.OrderLineItemInternalDto;
import com.team_e_commerce.core.claim.dto.ClaimCreateRequest;
import com.team_e_commerce.core.claim.dto.ClaimResponse;
import com.team_e_commerce.core.claim.dto.ClaimSearchCondition;
import com.team_e_commerce.core.claim.entity.CancelClaim;
import com.team_e_commerce.core.claim.entity.Claim;
import com.team_e_commerce.core.claim.entity.ExchangeClaim;
import com.team_e_commerce.core.claim.entity.ReturnClaim;
import com.team_e_commerce.core.claim.event.AutoCancelProcessEvent;
import com.team_e_commerce.core.claim.event.ClaimWithdrawnEvent;
import com.team_e_commerce.core.claim.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Facade 계층에서 타 모듈의 데이터를 조회해 온 뒤 호출됩니다.
    @Transactional
    public List<ClaimResponse> createClaimsLocalTx(Long memberId, ClaimCreateRequest request, Map<Long, OrderLineItemInternalDto> orderItemMap) {
        List<Claim> claimsToSave = new ArrayList<>();

        // 환불 계좌 매핑 (공통 필드)
        Claim.RefundAccount refundAccount = null;
        if (request.refundAccount() != null) {
            refundAccount = Claim.RefundAccount.builder()
                    .bankName(request.refundAccount().bankName())
                    .accountNumber(request.refundAccount().accountNumber())
                    .accountHolder(request.refundAccount().accountHolder())
                    .build();
        }

        List<Long> requestedItemIds = request.claimItems().stream()
                .map(ClaimCreateRequest.ClaimItem::orderLineItemId)
                .toList();

        // 중복 클레임 검증
        List<Long> pendingClaimItemIds = claimRepository.findPendingClaimItemIds(
                requestedItemIds,
                List.of(Claim.ClaimStatus.REQUESTED, Claim.ClaimStatus.PROCESSING)
        );

        for (ClaimCreateRequest.ClaimItem reqItem : request.claimItems()) {
            Long itemId = reqItem.orderLineItemId();

            if (pendingClaimItemIds.contains(itemId)) {
                throw new BusinessException(ErrorCode.ALREADY_CLAIMED);
            }

            OrderLineItemInternalDto orderItem = orderItemMap.get(itemId);
            if (orderItem == null) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
            }
            if (!orderItem.memberId().equals(memberId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }

            // 다형성 엔티티 생성 및 타입별 필수값(수거지 주소) 검증
            Claim claim = createPolymorphicClaimEntity(request, reqItem, orderItem, memberId, refundAccount);
            claimsToSave.add(claim);
        }

        // DB 저장
        return claimRepository.saveAll(claimsToSave).stream()
                .map(ClaimResponse::from)
                .toList();
    }

    // 시스템 자동 부분 취소 (로컬 DB 저장 및 비동기 이벤트 발행)
    @Transactional
    public void processAutoCancelLocalTx(Long memberId, List<OrderLineItemInternalDto> items, String reason) {
        List<Claim> claimsToSave = new ArrayList<>();

        for (OrderLineItemInternalDto item : items) {
            if (!item.memberId().equals(memberId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }

            // 기존 주문 상태 검증 로직 유지
            switch (item.orderStatus()) {
                case "WAITING_DEPOSIT", "PAYMENT_COMPLETED" -> {
                    // 자동 취소는 CANCEL 타입 고정
                    CancelClaim claim = CancelClaim.builder()
                            .orderLineItemId(item.orderLineItemId())
                            .memberId(memberId)
                            .productName(item.productName())
                            .reason(reason)
                            .claimAmount(item.actualPayAmount() != null ? item.actualPayAmount() : 0L)
                            .claimQuantity(1L)
                            .orderNumber(item.orderNumber())
                            // paymentMethod 등은 item에서 추출하거나 생략(비즈니스 룰에 맞게)
                            .build();
                    claimsToSave.add(claim);
                }
                case "PREPARING_PRODUCT", "SHIPPING", "DELIVERED" -> throw new BusinessException(ErrorCode.INVALID_CLAIM_STATUS);
                case "CANCELED" -> throw new BusinessException(ErrorCode.ALREADY_CLAIMED);
                default -> throw new BusinessException(ErrorCode.INVALID_CLAIM_STATUS);
            }
        }

        claimRepository.saveAll(claimsToSave);

        // 환불 데이터 집계
        Map<String, Long> refundAmountMap = items.stream()
                .filter(i -> "PAYMENT_COMPLETED".equals(i.orderStatus()))
                .filter(i -> i.actualPayAmount() != null && i.actualPayAmount() > 0)
                .collect(Collectors.groupingBy(
                        OrderLineItemInternalDto::paymentKey,
                        Collectors.summingLong(OrderLineItemInternalDto::actualPayAmount)
                ));

        List<Long> orderLineItemIds = items.stream().map(OrderLineItemInternalDto::orderLineItemId).toList();

        // 트랜잭션 커밋 완료 후 환불 등 호출하도록 이벤트 발행
        eventPublisher.publishEvent(new AutoCancelProcessEvent(memberId, orderLineItemIds, refundAmountMap, reason));
    }

    // 사용자 클레임 철회
    @Transactional
    public void withdrawClaim(Long memberId, Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));

        if (!claim.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 엔티티 내부 메서드를 통한 상태 전이 검증 및 실행
        claim.withdraw();

        // 철회에 따른 후속 처리 이벤트 발행
        eventPublisher.publishEvent(new ClaimWithdrawnEvent(claimId, claim.getOrderLineItemId()));
    }

    // 고객 본인 클레임 내역 조회
    @Transactional(readOnly = true)
    public Page<ClaimResponse> getClaimHistory(Long memberId, ClaimSearchCondition condition, Pageable pageable) {
        // 1. Repository에서 QueryDSL을 통해 조건에 맞는 다형성 엔티티(List<Claim>) 페이징 조회
        Page<Claim> claimPage = claimRepository.searchClaims(memberId, condition, pageable);

        // 2. DTO 변환 (내부에서 ReturnClaim, ExchangeClaim 여부를 판별하여 물류 주소 자동 바인딩)
        return claimPage.map(ClaimResponse::from);
    }

    private Claim createPolymorphicClaimEntity(ClaimCreateRequest request,
                                               ClaimCreateRequest.ClaimItem reqItem,
                                               OrderLineItemInternalDto orderItem,
                                               Long memberId,
                                               Claim.RefundAccount refundAccount) {

        // 반품, 교환 시 수거지 주소 필수값 검증
        if (("RETURN".equals(request.claimType()) || "EXCHANGE".equals(request.claimType()))
                && (request.returnAddress() == null || request.returnAddress().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return switch (request.claimType()) {
            case "CANCEL" -> CancelClaim.builder()
                    .orderLineItemId(reqItem.orderLineItemId())
                    .memberId(memberId)
                    .productName(orderItem.productName())
                    .reason(request.reason())
                    .claimAmount(orderItem.actualPayAmount()) // 단가 단위 계산 권장
                    .claimQuantity((long) reqItem.quantity())
                    .imageUrls(request.imageUrls())
                    .paymentMethod(request.paymentMethod())
                    .refundAccount(refundAccount)
                    .orderNumber(orderItem.orderNumber())
                    .build();

            case "RETURN" -> ReturnClaim.builder()
                    .orderLineItemId(reqItem.orderLineItemId())
                    .memberId(memberId)
                    .productName(orderItem.productName())
                    .reason(request.reason())
                    .claimAmount(orderItem.actualPayAmount())
                    .claimQuantity((long) reqItem.quantity())
                    .imageUrls(request.imageUrls())
                    .paymentMethod(request.paymentMethod())
                    .refundAccount(refundAccount)
                    .orderNumber(orderItem.orderNumber())
                    .returnAddress(request.returnAddress())
                    .build();

            case "EXCHANGE" -> ExchangeClaim.builder()
                    .orderLineItemId(reqItem.orderLineItemId())
                    .memberId(memberId)
                    .productName(orderItem.productName())
                    .reason(request.reason())
                    .claimAmount(0L)
                    .claimQuantity((long) reqItem.quantity())
                    .imageUrls(request.imageUrls())
                    .paymentMethod(request.paymentMethod())
                    .refundAccount(refundAccount)
                    .orderNumber(orderItem.orderNumber())
                    .returnAddress(request.returnAddress())
                    .build();

            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        };
    }
}