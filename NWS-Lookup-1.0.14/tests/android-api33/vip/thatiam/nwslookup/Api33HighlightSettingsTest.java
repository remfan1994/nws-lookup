package vip.thatiam.nwslookup;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The same focused feature checks on Android 13, with an explicit runtime assertion. */
@Config(sdk = 33)
public class Api33HighlightSettingsTest extends HighlightSettingsTest {
    @Before
    public void verifyAndroid13Runtime() {
        assertEquals(33, RuntimeEnvironment.getApiLevel());
    }
}
