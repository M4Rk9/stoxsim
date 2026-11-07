package com.stoxsim.learner.api;

public record LearnerHome(LearnerProfile profile, Recommendation recommendation) {
    public record Recommendation(String id, String title, String description, String href, String actionLabel) { }
}
