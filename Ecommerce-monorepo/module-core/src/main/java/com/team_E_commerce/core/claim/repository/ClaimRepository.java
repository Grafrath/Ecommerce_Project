package com.team_E_commerce.core.claim.repository;

import com.team_E_commerce.core.claim.domain.Claim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long>, ClaimRepositoryCustom {
    boolean existsPendingClaimByOrderLineItemId(Long itemId);
}