package com.team_e_commerce.core.infrastructure.redis.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EventOutboxRepository extends JpaRepository<EventOutbox, Long> {

    // 재발행 스케줄러용: 발행되지 않았고, 재시도 횟수가 한도(5회) 미만인 데이터 조회
    List<EventOutbox> findByPublishedFalseAndRetryCountLessThan(int maxRetries);

    // 삭제 스케줄러용: 특정 시점 이전의 발행 완료된 데이터 일괄 삭제
    long deleteByPublishedTrueAndCreatedAtBefore(LocalDateTime threshold);

    // 발행되지 않은(published = false) 이벤트 전체 조회
    List<EventOutbox> findByPublishedFalse();

}