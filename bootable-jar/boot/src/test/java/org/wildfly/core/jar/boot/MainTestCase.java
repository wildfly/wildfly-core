/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.boot;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class MainTestCase {

    @Test
    public void testSecmgrOptionRejected() {
        assertThrows(Exception.class, () -> Main.main(new String[] {"-secmgr"}));
    }

    @Test
    public void testSecurityManagerSystemPropertyArgumentsRejected() {
        for (String argument : new String[] {"-Djava.security.manager", "-Djava.security.manager=allow"}) {
            assertThrows(Exception.class, () -> Main.main(new String[] {argument}));
        }
    }

    @Test
    public void testSecurityManagerSystemPropertyRejected() {
        try {
            for (String value : new String[] {"", "allow"}) {
                System.setProperty("java.security.manager", value);
                assertThrows(Exception.class, () -> Main.main(new String[0]));
            }
        } finally {
            System.clearProperty("java.security.manager");
        }
    }

    // test that missing zipped server fails.
    @Test
    public void test() throws Exception {
        boolean fail = false;
        try {
            String[] args = {};
            Main.main(args);
            fail = true;
        } catch (Exception ex) {
            // XXX OK expected.
        }
        if (fail) {
            throw new Exception("Test should have failed");
        }
    }
}
