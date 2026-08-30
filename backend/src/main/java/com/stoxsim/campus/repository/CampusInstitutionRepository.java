package com.stoxsim.campus.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.campus.domain.CampusInstitution;

public interface CampusInstitutionRepository
    extends JpaRepository<CampusInstitution, UUID> {

    boolean existsByNormalizedName(String normalizedName);

    boolean existsByEmailDomain(String emailDomain);
}
