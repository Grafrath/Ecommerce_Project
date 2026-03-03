// 1. 주문 상태
public enum OrderStatus {
    PENDING_PAYMENT("결제 대기"),
    PAID("결제 완료"),
    ORDER_CANCELED("주문 전체 취소"); // 배송 전 취소 시 사용

    private final String description;
    // 생성자, Getter 생략, Lombok 활용
}

// 2. 배송 상태
public enum DeliveryStatus {
    PREPARING("상품 준비 및 포장 중"),
    SHIPPED("택배사 인계 및 배송 중"),
    DELIVERED("배송 완료");
}

// 3. 프로모션(쿠폰) 상태
public enum CouponStatus {
    ISSUED("발급됨 - 사용 가능"),
    USED("결제에 사용됨"),
    RESTORED("환불되어 복구됨 - 사용 가능"),
    EXPIRED("기간 만료됨 - 사용 불가");

    public CouponStatus determineRollbackStatus(LocalDateTime expirationDate) {
        if (LocalDateTime.now().isAfter(expirationDate)) {
            return EXPIRED; // 합의된 원칙: 기간 지났으면 가차 없이 소멸
        }
        return RESTORED; // 기간 남았으면 복구
    }
}

// 4. 재고 상태
public enum InventoryStatus {
    IN_STOCK("재고 있음"),
    OUT_OF_STOCK("품절");
}

// 5. 클레임 상태
public enum ClaimStatus {
    REQUESTED("클레임 요청됨"),
    PROCESSING("클레임 처리 중 (상품 수거 등)"),
    COMPLETED("클레임 완료 (환불/교환 완료 및 쿠폰/재고 롤백 완료)"),
    REJECTED("클레임 반려됨 (고객 과실 등)");
}