package sc.fiji.snt.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JavaVersionBridgeTest {

    @Test
    public void testParseJava8() {
        assertEquals(8, JavaVersionBridge.parseMajorVersion("1.8.0_392"));
        assertEquals(8, JavaVersionBridge.parseMajorVersion("1.8.0"));
    }

    @Test
    public void testParseJava6() {
        assertEquals(6, JavaVersionBridge.parseMajorVersion("1.6.0_45"));
    }

    @Test
    public void testParseJava11() {
        assertEquals(11, JavaVersionBridge.parseMajorVersion("11.0.21"));
        assertEquals(11, JavaVersionBridge.parseMajorVersion("11"));
    }

    @Test
    public void testParseJava21() {
        assertEquals(21, JavaVersionBridge.parseMajorVersion("21.0.2"));
        assertEquals(21, JavaVersionBridge.parseMajorVersion("21"));
    }

    @Test
    public void testParseEarlyAccess() {
        assertEquals(22, JavaVersionBridge.parseMajorVersion("22-ea"));
    }

    @Test
    public void testParseEdgeCases() {
        assertEquals(0, JavaVersionBridge.parseMajorVersion(null));
        assertEquals(0, JavaVersionBridge.parseMajorVersion(""));
    }

    @Test
    public void testCurrentJvmDetected() {
        assertTrue("Should detect a positive Java version",
                JavaVersionBridge.getMajorJavaVersion() > 0);
    }
}
