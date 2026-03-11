package com.team_E_commerce.core.claim.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team_E_commerce.core.claim.domain.Claim;
import com.team_E_commerce.core.claim.dto.ClaimSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.team_E_commerce.core.claim.domain.QClaim.claim;

@RequiredArgsConstructor
public class ClaimRepositoryImpl implements ClaimRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Claim> searchClaims(Long memberId, ClaimSearchCondition cond, Pageable pageable) {

        // 실제 데이터를 가져오는 메인 쿼리
        List<Claim> content = queryFactory
                .selectFrom(claim)
                .where(
                        claim.memberId.eq(memberId), // 필수 조건
                        eqClaimType(cond.claimType()), // 동적 조건들 (null이면 무시됨)
                        eqClaimStatus(cond.claimStatus()),
                        betweenDate(cond.startDate(), cond.endDate())
                )
                .orderBy(claim.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 2. 전체 개수를 세는 Count 쿼리 분리
        JPAQuery<Long> countQuery = queryFactory
                .select(claim.count())
                .from(claim)
                .where(
                        claim.memberId.eq(memberId),
                        eqClaimType(cond.claimType()),
                        eqClaimStatus(cond.claimStatus()),
                        betweenDate(cond.startDate(), cond.endDate())
                );

        // 3. 최적화된 페이징 객체 반환
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 동적 쿼리 조립용 BooleanExpression 메서드들
    private BooleanExpression eqClaimType(String type) {
        return StringUtils.hasText(type) ? claim.claimType.eq(Claim.ClaimType.valueOf(type)) : null;
    }

    private BooleanExpression eqClaimStatus(String status) {
        return StringUtils.hasText(status) ? claim.claimStatus.eq(Claim.ClaimStatus.valueOf(status)) : null;
    }

    private BooleanExpression betweenDate(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return claim.createdAt.between(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        }
        return null;
    }
}