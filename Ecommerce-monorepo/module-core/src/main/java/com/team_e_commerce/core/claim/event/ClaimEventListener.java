package com.team_e_commerce.core.claim.event;

import com.team_e_commerce.core.claim.entity.EventOutbox;
import com.team_e_commerce.core.claim.repository.EventOutboxRepository;
import com.team_e_commerce.core.claim.service.ClaimMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimEventListener {

    private final EventOutboxRepository outboxRepository;
    private final ClaimMessagePublisher messagePublisher;

    @Async // 즉시 발행 시 메인 스레드 방해 금지
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEvent(Long outboxId) {
        // 1. 저장된 아웃박스 데이터 조회
        EventOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.isPublished()) return;

        try {
            // 2. 외부 메시지 발행 (실제 전송)
            messagePublisher.publish(outbox.getEventType(), outbox.getPayload());

            // 3. 발행 성공 상태 업데이트 (새로운 트랜잭션 필요)
            updatePublishedStatus(outboxId);

        } catch (Exception e) {
            log.error("이벤트 발행 실패 - OutboxId: {}, 사유: {}", outboxId, e.getMessage());
            // 여기서 별도의 재시도 횟수를 증가시키거나 로그를 남길 수 있습니다.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePublishedStatus(Long outboxId) {
        outboxRepository.findById(outboxId).ifPresent(EventOutbox::markAsPublished);
    }
}