package com.stoxsim.onboarding.service;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class GuidedJourneyServiceTest {
    @Test void waitsForExecutionAndRetainsLearningAlreadyDone() {
        var waiting = GuidedJourneyService.progress(true, false, true, true, false, false, false);
        assertThat(waiting.nextStep()).isEqualTo("WAITING");
        assertThat(waiting.firstTradeExecuted()).isFalse();
        assertThat(waiting.completed()).isFalse();
        assertThat(GuidedJourneyService.progress(true, true, false, true, false, false, false).nextStep()).isEqualTo("SCENARIO");
    }
    @Test void progressesThroughAllFeaturesWithoutRequiringCompetitionEnrollment() {
        assertThat(GuidedJourneyService.progress(false,false,false,false,false,false,false).nextStep()).isEqualTo("INTRODUCTION");
        assertThat(GuidedJourneyService.progress(true,false,false,false,false,false,false).nextStep()).isEqualTo("FIRST_TRADE");
        assertThat(GuidedJourneyService.progress(true,true,false,false,false,false,false).nextStep()).isEqualTo("FINWIZ");
        assertThat(GuidedJourneyService.progress(true,true,false,true,true,false,false).nextStep()).isEqualTo("LEARNING");
        assertThat(GuidedJourneyService.progress(true,true,false,true,true,true,false).nextStep()).isEqualTo("COMPETITIONS");
        var complete = GuidedJourneyService.progress(true,true,false,true,true,true,true);
        assertThat(complete.completed()).isTrue();
        assertThat(complete.completedSteps()).isEqualTo(6);
    }
}
