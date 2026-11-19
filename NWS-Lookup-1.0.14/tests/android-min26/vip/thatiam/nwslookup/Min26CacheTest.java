package vip.thatiam.nwslookup;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Runs selected cache regressions on the minimum supported Android framework. */
@Config(sdk = 26)
public class Min26CacheTest extends CacheLongevityTest {
    @Before public void verifyMinimumApiRuntime() {
        assertEquals(26, RuntimeEnvironment.getApiLevel());
    }
}
