package com.stoxsim.watchlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.stoxsim.watchlist.domain.WatchlistItem;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {

    @EntityGraph(attributePaths = "instrument")
    List<WatchlistItem> findAllByWatchlistIdOrderByCreatedAtDesc(UUID watchlistId);

    @EntityGraph(attributePaths = "instrument")
    Optional<WatchlistItem> findByIdAndWatchlistId(UUID id, UUID watchlistId);

    Optional<WatchlistItem> findByWatchlistIdAndInstrumentId(UUID watchlistId, UUID instrumentId);

    @Query("SELECT item FROM WatchlistItem item JOIN FETCH item.instrument")
    List<WatchlistItem> findAllWithInstrument();
}
