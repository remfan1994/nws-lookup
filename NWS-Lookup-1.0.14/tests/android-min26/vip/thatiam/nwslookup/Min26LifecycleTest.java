package vip.thatiam.nwslookup;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Runs selected Activity lifecycle regressions on Android 8.0. */
@Config(sdk = 26)
public class Min26LifecycleTest extends LifecycleSoakTest {
    @Before public void verifyMinimumApiRuntime() {
        assertEquals(26, RuntimeEnvironment.getApiLevel());
    }
}
