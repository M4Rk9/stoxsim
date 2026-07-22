package com.stoxsim.watchlist.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stoxsim.watchlist.domain.Watchlist;

public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {

    Optional<Watchlist> findByUserIdAndDefaultWatchlistTrue(UUID userId);
}
