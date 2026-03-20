package com.team_e_commerce.core.infrastructure.redis.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final EventOutboxRepository outboxRepository;
    private final StringRedisTemplate redisTemplate; // Redis Streams 발행용

    // 10초 주기로 발송되지 않은 이벤트 폴링
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void publishOutboxEvents() {
        List<EventOutbox> unpublishedEvents = outboxRepository.findByPublishedFalse();

        for (EventOutbox event : unpublishedEvents) {
            try {
                // Redis Streams에 데이터 발행
                redisTemplate.opsForStream().add(
                        Record.of(Map.of("payload", event.getPayload()))
                                .withStreamKey("stream:" + event.getEventType())
                );

                // 전송 성공 시 상태 업데이트
                event.markAsPublished();
            } catch (Exception e) {
                log.error("Redis Streams 발행 실패 - eventId: {}", event.getId(), e);
                // 실패한 건은 다음 스케줄러 턴에 자동으로 재시도됨 (At-Least-Once 보장)
            }
        }
    }
}