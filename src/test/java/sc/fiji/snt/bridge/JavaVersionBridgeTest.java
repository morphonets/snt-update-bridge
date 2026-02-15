/*-
 * #%L
 * A Java 8-compiled bridge that warns users when SNT
 *         requires a newer Java runtime than what is currently available.
 * %%
 * Copyright (C) 2026 Fiji developers
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
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
