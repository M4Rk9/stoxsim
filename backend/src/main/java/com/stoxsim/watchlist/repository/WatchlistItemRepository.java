package com.stoxsim.watchlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.watchlist.domain.WatchlistItem;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {

    @EntityGraph(attributePaths = "instrument")
    List<WatchlistItem> findAllByWatchlistIdOrderByCreatedAtDesc(UUID watchlistId);

    @EntityGraph(attributePaths = "instrument")
    Optional<WatchlistItem> findByIdAndWatchlistId(UUID id, UUID watchlistId);

    Optional<WatchlistItem> findByWatchlistIdAndInstrumentId(UUID watchlistId, UUID instrumentId);

    @Query("SELECT item FROM WatchlistItem item JOIN FETCH item.instrument")
    List<WatchlistItem> findAllWithInstrument();

    @Query("""
        SELECT COUNT(item)
        FROM WatchlistItem item
        WHERE item.watchlist.user.id = :userId
        """)
    long countByUserId(@Param("userId") UUID userId);
}
