package com.team_e_commerce.core.claim.event;

import java.util.List;

// 트랜잭션 종료 후 재고 모듈이 주워갈 이벤트 객체
public record OrderItemsCanceledEvent(
        List<Long> orderLineItemIds,
        Long memberId,
        String reason
) {}