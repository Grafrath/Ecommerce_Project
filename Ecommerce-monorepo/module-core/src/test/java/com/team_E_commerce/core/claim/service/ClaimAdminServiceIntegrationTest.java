package com.team_E_commerce.core.claim.service;

import com.team_E_commerce.core.claim.client.PaymentInternalClient;
import com.team_E_commerce.core.claim.client.SupportInternalClient;
import com.team_E_commerce.core.claim.domain.Claim;
import com.team_E_commerce.core.claim.repository.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional // 테스트 종료 후 테스트 데이터 롤백을 위해 사용
class ClaimAdminServiceIntegrationTest {

    @Autowired
    private ClaimAdminService claimAdminService;

    @Autowired
    private ClaimRepository claimRepository;

    @MockitoBean
    private PaymentInternalClient paymentInternalClient;

    @MockitoBean
    private SupportInternalClient supportInternalClient;

    private Claim savedClaim;
    private final Long ADMIN_ID = 999L;

    @BeforeEach
    void setUp() {
        // 테스트용 정상 클레임 데이터 세팅 (PENDING 상태)
        Claim claim = Claim.builder()
                .orderLineItemId(100L)
                .memberId(1L)
                .productName("테스트 상품")
                .orderNumber("20260313-0001")
                .claimType(Claim.ClaimType.RETURN)
                .reason("단순 변심")
                .claimAmount(50000L)
                .claimQuantity(1L)

                .build();

        savedClaim = claimRepository.save(claim);
    }

    @Test
    @DisplayName("관리자 환불 승인 - 백오피스 연동 실패 시 3회 재시도 후 전체 롤백된다")
    void approveClaim_SupportClientFails_Retries3TimesAndRollsBack() {
        // given: SupportClient가 호출될 때마다 무조건 예외(장애)를 던지도록 조작(Stubbing)
        doThrow(new RuntimeException("백오피스 서버 타임아웃 장애 발생!"))
                .when(supportInternalClient).sendAdminHistory(any());

        // when & then: 승인 로직을 실행하면, 결국 RuntimeException이 터져야 정상이다.
        assertThatThrownBy(() -> claimAdminService.approveClaim(ADMIN_ID, savedClaim.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("백오피스 서버 타임아웃 장애 발생!");

        // ★ [핵심 검증 1] 결제 취소 API는 정상적으로 1번 호출되었는가? (이후 롤백됨)
        verify(paymentInternalClient, times(1)).cancelPayment((Long) anyLong(), (Long) anyLong());

        // ★ [핵심 검증 2] 백오피스 API는 우리가 설정한 대로 정확히 3번(최초1회 + 재시도2회) 찔러보았는가?
        verify(supportInternalClient, times(3)).sendAdminHistory(any());

        // ★ [핵심 검증 3] 트랜잭션이 롤백되어, 엔티티의 상태가 'COMPLETED'로 바뀌지 않고 그대로 유지되었는가?
        // (JPA 영속성 컨텍스트 초기화 후 다시 조회하여 확인)
        Claim rollbackClaim = claimRepository.findById(savedClaim.getId()).get();
        assertThat(rollbackClaim.getClaimStatus()).isEqualTo(Claim.ClaimStatus.PROCESSING);
    }

    @Test
    @DisplayName("관리자 환불 승인 - 정상 흐름 시 모든 클라이언트가 1번씩 호출되고 상태가 변경된다")
    void approveClaim_Success() {
        // given: 아무 예외도 던지지 않는 정상 Mock 상태

        // when
        claimAdminService.approveClaim(ADMIN_ID, savedClaim.getId());

        // then
        verify(paymentInternalClient, times(1)).cancelPayment((Long) anyLong(), (Long) anyLong());
        verify(supportInternalClient, times(1)).sendAdminHistory(any());

        Claim completedClaim = claimRepository.findById(savedClaim.getId()).get();
        assertThat(completedClaim.getClaimStatus()).isEqualTo(Claim.ClaimStatus.COMPLETED);
    }

}