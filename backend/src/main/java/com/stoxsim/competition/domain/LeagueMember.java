package com.stoxsim.competition.domain;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.auth.domain.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "league_member")
public class LeagueMember {

    public enum Role { OWNER, MEMBER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private PrivateLeague league;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 16)
    private Role role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected LeagueMember() {
    }

    public LeagueMember(PrivateLeague league, AppUser user, Role role, Instant joinedAt) {
        this.league = league;
        this.user = user;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID getId() { return id; }
    public PrivateLeague getLeague() { return league; }
    public AppUser getUser() { return user; }
    public Role getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
}
