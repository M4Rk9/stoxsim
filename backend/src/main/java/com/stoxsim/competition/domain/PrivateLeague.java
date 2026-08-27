package com.stoxsim.competition.domain;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.auth.domain.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "private_league")
public class PrivateLeague {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private CompetitionSeason season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "invite_code_hash", nullable = false, unique = true, length = 64)
    private String inviteCodeHash;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PrivateLeague() {
    }

    public PrivateLeague(
        CompetitionSeason season,
        AppUser owner,
        String name,
        String inviteCodeHash,
        int maxMembers,
        Instant now
    ) {
        this.season = season;
        this.owner = owner;
        this.name = name;
        this.inviteCodeHash = inviteCodeHash;
        this.maxMembers = maxMembers;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rotateInviteCode(String inviteCodeHash, Instant now) {
        this.inviteCodeHash = inviteCodeHash;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public CompetitionSeason getSeason() { return season; }
    public AppUser getOwner() { return owner; }
    public String getName() { return name; }
    public String getInviteCodeHash() { return inviteCodeHash; }
    public int getMaxMembers() { return maxMembers; }
    public Instant getCreatedAt() { return createdAt; }
}
