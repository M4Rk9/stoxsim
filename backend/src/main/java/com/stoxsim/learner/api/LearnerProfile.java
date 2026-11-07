package com.stoxsim.learner.api;

import java.time.Instant;
import com.stoxsim.market.domain.MarketRegion;

public record LearnerProfile(ExperienceLevel experienceLevel, LearningObjective learningObjective,
                             MarketRegion preferredMarket, Instant updatedAt) {
    public enum ExperienceLevel { BEGINNER, INTERMEDIATE, ADVANCED }
    public enum LearningObjective { BASICS, FUNDAMENTALS, DIVERSIFICATION, DISCIPLINE }
    public record Update(ExperienceLevel experienceLevel, LearningObjective learningObjective,
                         MarketRegion preferredMarket) { }
    public static LearnerProfile empty() { return new LearnerProfile(null, null, null, null); }
}
