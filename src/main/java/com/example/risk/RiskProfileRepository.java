package com.example.risk;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskProfileRepository extends JpaRepository<RiskProfile, Long> {

    Optional<RiskProfile> findByUserId(Long userId);
}

