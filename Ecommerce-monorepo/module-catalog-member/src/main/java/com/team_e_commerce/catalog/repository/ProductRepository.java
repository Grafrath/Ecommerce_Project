package com.team_e_commerce.catalog.repository;

import com.team_e_commerce.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}