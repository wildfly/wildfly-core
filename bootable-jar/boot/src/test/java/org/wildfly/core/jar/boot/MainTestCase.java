/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.boot;

import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class MainTestCase {

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
