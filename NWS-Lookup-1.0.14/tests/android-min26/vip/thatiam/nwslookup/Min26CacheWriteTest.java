package vip.thatiam.nwslookup;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Select the interrupted-write and legacy-backup tests; .new recovery is API35-specific. */
@Config(sdk = 26)
public class Min26CacheWriteTest extends CacheWriteFailureTest {
    @Before public void verifyMinimumApiRuntime() {
        assertEquals(26, RuntimeEnvironment.getApiLevel());
    }
}
