package com.team_E_commerce.core.claim.client.dto;

public record OrderLineItemInternalDto(
        Long orderLineItemId,
        Long memberId,
        String productName,
        Integer unitPrice,
        Integer cancelableQuantity,
        String orderStatus
) {}