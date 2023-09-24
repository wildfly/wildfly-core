/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.logging.module;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        CustomModuleFilterTestCase.class,
        CustomModuleFormatterTestCase.class,
        CustomModuleHandlerTestCase.class,
})
public class CustomModuleTestSuite {

    @BeforeClass
    public static void createModules() {
        TestEnvironment.createModules();
    }

    @AfterClass
    public static void deleteModules() {
        TestEnvironment.deleteModules();
    }
}
