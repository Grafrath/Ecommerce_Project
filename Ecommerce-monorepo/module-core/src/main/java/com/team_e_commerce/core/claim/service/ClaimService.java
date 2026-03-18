package com.team_e_commerce.core.claim.service;

import com.team_e_commerce.common.exception.BusinessException;
import com.team_e_commerce.common.exception.ErrorCode;
import com.team_e_commerce.core.claim.client.OrderInternalClient;
import com.team_e_commerce.core.claim.client.dto.OrderLineItemInternalDto;
import com.team_e_commerce.core.claim.domain.Claim;
import com.team_e_commerce.core.claim.dto.ClaimCreateRequest;
import com.team_e_commerce.core.claim.dto.ClaimResponse;
import com.team_e_commerce.core.claim.dto.ClaimSearchCondition;
import com.team_e_commerce.core.claim.event.ClaimWithdrawnEvent;
import com.team_e_commerce.core.claim.event.OrderItemsCanceledEvent;
import com.team_e_commerce.core.claim.repository.ClaimRepository;
import com.team_e_commerce.core.payment.infrastructure.PaymentGatewayClient;
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
    private final OrderInternalClient orderInternalClient;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentGatewayClient pgClient;

    @Transactional
    public List<ClaimResponse> createClaims(Long memberId, ClaimCreateRequest request) {
        // 리스트에 모아서 한 번에 저장합니다.
        List<Claim> claimsToSave = new ArrayList<>();

        // 환불 계좌 정보 매핑
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

        // 1. 주문 데이터를 단 한 번의 요청으로 가져와 Map으로 변환
        Map<Long, OrderLineItemInternalDto> orderItemMap = orderInternalClient.getOrderItems(requestedItemIds)
                .stream()
                .collect(Collectors.toMap(OrderLineItemInternalDto::orderLineItemId, item -> item));

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
                log.error("존재하지 않는 주문 상세 ID 클레임 요청 - memberId: {}, itemId: {}", memberId, itemId);
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
            }

            if (!orderItem.memberId().equals(memberId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }

            if (orderItem.cancelableQuantity() < reqItem.quantity()) {
                throw new BusinessException(ErrorCode.EXCEED_CANCELABLE_QUANTITY);
            }

            Claim claim = Claim.builder()
                    .orderLineItemId(itemId)
                    .memberId(memberId)
                    .productName(orderItem.productName())
                    .claimType(Claim.ClaimType.valueOf(request.claimType()))
                    .reason(request.reason())
                    .claimAmount((long) (orderItem.unitPrice() * reqItem.quantity()))
                    .claimQuantity(Long.valueOf(reqItem.quantity()))
                    .imageUrls(request.imageUrls())
                    .refundAccount(refundAccount)
                    .orderNumber(orderItem.orderNumber())
                    .build();

            claimsToSave.add(claim);
        }

        List<Claim> savedClaims = claimRepository.saveAll(claimsToSave);

        return savedClaims.stream()
                .map(ClaimResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> getClaimHistory(Long memberId, ClaimSearchCondition condition, Pageable pageable) {
        Page<Claim> claims = claimRepository.searchClaims(memberId, condition, pageable);
        return claims.map(ClaimResponse::from);
    }

    @Transactional
    public void withdrawClaim(Long memberId, Long claimId) {
        Claim claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLAIM_NOT_FOUND));

        if (!claim.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        claim.withdraw();

        eventPublisher.publishEvent(new ClaimWithdrawnEvent(
                claim.getId(),
                claim.getOrderLineItemId(),
                claim.getMemberId()
        ));

        log.info("고객 클레임 철회 트랜잭션 정상 종료 - claimId: {}", claimId);
    }

    @Transactional
    public void autoCancelClaimItems(Long memberId, List<Long> orderLineItemIds, String reason) {

        // 1. 주문 모듈에서 데이터 일괄 조회
        List<OrderLineItemInternalDto> items = orderInternalClient.getOrderItems(orderLineItemIds);

        if (items.isEmpty() || items.size() != orderLineItemIds.size()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        // 2. 권한 검증 및 상태별 분류
        List<Claim> claimsToSave = new ArrayList<>();

        for (OrderLineItemInternalDto item : items) {
            if (!item.memberId().equals(memberId)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }

            // Java 21 Switch로 상태별 철저한 라우팅 검증
            switch (item.orderStatus()) {
                case "WAITING_DEPOSIT", "PAYMENT_COMPLETED" -> {
                    // 정상적인 자동 취소 가능 상태
                    claimsToSave.add(createCancelClaimEntity(item, reason));
                }
                case "PREPARING_PRODUCT" -> throw new BusinessException(ErrorCode.INVALID_CLAIM_STATUS);
                case "SHIPPING", "DELIVERED" -> throw new BusinessException(ErrorCode.INVALID_CLAIM_STATUS);
                case "CANCELED" -> throw new BusinessException(ErrorCode.ALREADY_CLAIMED);
                default -> throw new BusinessException(ErrorCode.INVALID_CLAIM_STATUS);
            }
        }

        // 3. PG 환불 최적화 (동일한 paymentKey의 환불 금액을 합산하여 1번만 호출)
        Map<String, Long> refundAmountMap = items.stream()
                .filter(i -> "PAYMENT_COMPLETED".equals(i.orderStatus()))
                .filter(i -> i.actualPayAmount() != null && i.actualPayAmount() > 0)
                .collect(Collectors.groupingBy(
                        OrderLineItemInternalDto::paymentKey,
                        Collectors.summingLong(OrderLineItemInternalDto::actualPayAmount)
                ));

        refundAmountMap.forEach((paymentKey, totalRefundAmount) -> {
            log.info("PG 환불 요청 - 결제키: {}, 총 환불금액: {}", paymentKey, totalRefundAmount);
            pgClient.refund(paymentKey, totalRefundAmount);

            // TODO: (총 주문단가 - actualPayAmount)를 계산하여 포인트/쿠폰 원복 파이프라인 호출
        });

        // 4. DB 상태 변경 위임 및 클레임 이력 저장
        orderInternalClient.cancelOrderItems(orderLineItemIds); // 주문 모듈에 상태 변경 명령
        claimRepository.saveAll(claimsToSave);

        // 5. 트랜잭션이 성공적으로 커밋된 '후'에 재고 원복을 지시하는 이벤트 발행
        eventPublisher.publishEvent(new OrderItemsCanceledEvent(orderLineItemIds, memberId, reason));

        log.info("자동 부분 취소 트랜잭션 정상 완료 - memberId: {}", memberId);
    }

    // 엔티티 생성용 내부 편의 메서드 (빌더 에러 완벽 해결)
    private Claim createCancelClaimEntity(OrderLineItemInternalDto item, String reason) {
        Claim claim = Claim.builder()
                .orderLineItemId(item.orderLineItemId())
                .memberId(item.memberId())
                .productName(item.productName())
                .claimType(Claim.ClaimType.CANCEL) // 취소 타입 지정
                .reason(reason)
                .claimAmount(item.actualPayAmount() != null ? item.actualPayAmount() : 0L)
                .claimQuantity(1L) // 부분 취소 시 수량
                .orderNumber(item.orderNumber())
                .build();

        // 2. 자동 취소(환불)가 정상적으로 진행되었으므로, 메서드를 호출하여 상태를 즉시 완료로 전이
        claim.complete();

        return claim;
    }

}