package io.uhndata.cards;

import java.time.Instant;
import java.util.TimeZone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link DateUtils}.
 *
 * @version $Id$
 */
@RunWith(MockitoJUnitRunner.class)
public class DateUtilsTest
{
    @Test
    public void getTimezoneForDateString()
    {
        String timezone = DateUtils.getTimezoneForDateString("2023-01-01");
        String expectedTimezone = TimeZone.getDefault().toZoneId().getRules().getStandardOffset(Instant.now()).getId();
        assertEquals(expectedTimezone, timezone);
    }

    @Test
    public void getTimezoneForDateStringCatchesException()
    {
        String timezone = DateUtils.getTimezoneForDateString("notParsable");
        String expectedTimezone = TimeZone.getDefault().toZoneId().getRules().getOffset(Instant.now()).getId();
        assertEquals(expectedTimezone, timezone);
    }

}
