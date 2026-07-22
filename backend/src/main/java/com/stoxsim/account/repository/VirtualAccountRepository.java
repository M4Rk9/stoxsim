package com.stoxsim.account.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.account.domain.VirtualAccount;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    List<VirtualAccount> findAllByUserIdOrderByMarketRegion(UUID userId);
}
