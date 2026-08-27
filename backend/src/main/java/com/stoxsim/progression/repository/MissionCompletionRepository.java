package com.stoxsim.progression.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.progression.domain.MissionCompletion;

public interface MissionCompletionRepository
    extends JpaRepository<MissionCompletion, UUID> {

    List<MissionCompletion> findAllByUserIdOrderByCompletedAt(UUID userId);
}
