package com.stoxsim.analytics.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.stoxsim.analytics.repository.*;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.domain.AppUser;
class CohortAnalyticsServiceTest {
    @Test void deniesLearnersAndDeletedUsersBeforeQueryingCohorts() {
        var users=mock(AppUserRepository.class);var activity=mock(ActivityRepository.class);var cohorts=mock(CohortRepository.class);
        var service=new CohortAnalyticsService(users,activity,cohorts,Clock.systemUTC());var id=UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(new AppUser("example@test.example","unused","Example")));
        assertThatThrownBy(()->service.overview(id,null,null,CohortRepository.Group.WEEK)).hasMessageContaining("403");
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.overview(id,null,null,CohortRepository.Group.WEEK)).hasMessageContaining("User no longer exists");
        verifyNoInteractions(activity,cohorts);
    }
}
