package com.team_E_commerce.core.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 비회원 결제 허용 (nullable = true)
    @Column(name = "member_id")
    private Long memberId;

    // --- 주문자 스냅샷 정보 ---
    @Column(nullable = false)
    private String ordererName;

    @Column(nullable = false)
    private String ordererPhone;

    // --- 배송지 스냅샷 정보 ---
    @Column(nullable = false)
    private String zipcode;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String detailAddress;

    // --- 금액 및 상태 ---
    @Column(nullable = false)
    private Integer totalAmount;

    @Column(nullable = false)
    private String orderStatus;

    // 부모-자식 연관관계
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineItem> lineItems = new ArrayList<>();

    @Builder
    public Order(Long memberId, String ordererName, String ordererPhone, String zipcode, String address, String detailAddress, Integer totalAmount, String orderStatus) {
        this.memberId = memberId;
        this.ordererName = ordererName;
        this.ordererPhone = ordererPhone;
        this.zipcode = zipcode;
        this.address = address;
        this.detailAddress = detailAddress;
        this.totalAmount = totalAmount;
        this.orderStatus = orderStatus;
    }

    public void addLineItem(OrderLineItem lineItem) {
        lineItems.add(lineItem);
        lineItem.setOrder(this);
    }
}