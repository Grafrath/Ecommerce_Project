package com.team_e_commerce.core.infrastructure.redis.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j // 💡 로그 기록을 위해 추가
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final EventOutboxRepository outboxRepository;
    private final StringRedisTemplate redisTemplate;

    // 10초 주기로 발송되지 않은 이벤트 폴링
    @Scheduled(fixedDelay = 10000)
    @Transactional // 💡 jakarta 대신 Spring의 Transactional 사용 (기능 더 풍부함)
    public void publishOutboxEvents() {
        // 💡 무한 재시도 방지: 10회 미만 실패한 건만 가져옴
        List<EventOutbox> unpublishedEvents = outboxRepository.findByPublishedFalseAndRetryCountLessThan(10);

        for (EventOutbox event : unpublishedEvents) {
            try {
                // 💡 Record.of 컴파일 에러 해결: 가장 직관적인 Key, Map 전달 API 사용
                redisTemplate.opsForStream().add(
                        "stream:" + event.getEventType(),
                        Map.of("payload", event.getPayload())
                );

                // 전송 성공 시 상태 업데이트 및 에러 메시지 초기화
                event.markAsPublished();

            } catch (Exception e) {
                // 💡 실패 시 DB에 이력 기록 (엔티티 메서드 활용)
                event.increaseRetryCount();
                event.markAsFailed(e.getMessage());

                log.error("Redis Streams 발행 실패 (재시도 {}/10) - eventId: {}", event.getRetryCount(), event.getId(), e);

                // 이번 실패로 10회 도달 시 Dead Letter 알림 로깅
                if (event.getRetryCount() >= 10) {
                    log.error("[CRITICAL] DEAD LETTER 발생: 이벤트 발행 영구 실패. 수동 개입 필요 - eventId: {}", event.getId());
                }
            }
        }
    }
}