package vip.thatiam.nwslookup;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Runs selected scripted-network regressions on Android 8.0. */
@Config(sdk = 26)
public class Min26NetworkTest extends NetworkLongevityTest {
    @Before public void verifyMinimumApiRuntime() {
        assertEquals(26, RuntimeEnvironment.getApiLevel());
    }
}
