package com.stoxsim.campus.domain;

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
@Table(name = "campus_membership")
public class CampusMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private CampusInstitution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 16)
    private CampusMemberRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected CampusMembership() {
    }

    public CampusMembership(
        CampusInstitution institution,
        AppUser user,
        CampusMemberRole role,
        Instant joinedAt
    ) {
        this.institution = institution;
        this.user = user;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID getId() {
        return id;
    }

    public CampusInstitution getInstitution() {
        return institution;
    }

    public CampusMemberRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
