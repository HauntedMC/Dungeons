package nl.hauntedmc.dungeons.content.function.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;

class RewardCooldownContractTest {
    @Test
    void monthlyRewardResetKeepsConfiguredDayAfterLeavingAShortMonth() {
        ZonedDateTime now = ZonedDateTime.parse("2027-02-28T09:00:00+01:00[Europe/Amsterdam]");
        Clock fixed = Clock.fixed(now.toInstant(), now.getZone());
        Calendar calendar = GregorianCalendar.from(now);
        try (var paper = new PaperBoundary();
                var calendars = mockStatic(Calendar.class, CALLS_REAL_METHODS);
                var clocks = mockStatic(Clock.class, CALLS_REAL_METHODS)) {
            calendars.when(Calendar::getInstance).thenAnswer(call -> calendar.clone());
            clocks.when(Clock::systemDefaultZone).thenReturn(fixed);
            var reward = new MonthlyReward();
            assertEquals(
                    ZonedDateTime.parse("2027-03-31T08:00:00+02:00[Europe/Amsterdam]").toInstant(),
                    reward.getCooldownTime().toInstant());
        }
    }

    static class MonthlyReward extends RewardFunction {
        MonthlyReward() {
            period = 4;
            cooldownTime = 8;
            resetDay = 31;
        }
    }
}
