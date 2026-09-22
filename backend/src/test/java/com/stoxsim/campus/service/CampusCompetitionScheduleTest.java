package com.stoxsim.campus.service;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
class CampusCompetitionScheduleTest {
    @Test void validatesTimeBoundariesAndCapacity() {
        var now=Instant.parse("2026-09-21T00:00:00Z");
        CampusCompetitionService.validateSchedule(now,now.plusSeconds(3600),now,2);
        CampusCompetitionService.validateSchedule(now.plusSeconds(90*86400),now.plusSeconds(180*86400),now,200);
        for(int capacity:new int[]{1,201}) assertThatThrownBy(()->CampusCompetitionService.validateSchedule(now,now.plusSeconds(3600),now,capacity)).isInstanceOf(ResponseStatusException.class);
        for(long duration:new long[]{3599,90*86400+1}) assertThatThrownBy(()->CampusCompetitionService.validateSchedule(now,now.plusSeconds(duration),now,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->CampusCompetitionService.validateSchedule(now.minusSeconds(1),now.plusSeconds(3600),now,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->CampusCompetitionService.validateSchedule(now.plusSeconds(90*86400+1),now.plusSeconds(91*86400),now,20)).isInstanceOf(ResponseStatusException.class);
    }
}
