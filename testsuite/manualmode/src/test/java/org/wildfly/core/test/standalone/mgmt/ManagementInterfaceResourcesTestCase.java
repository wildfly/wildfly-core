/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.test.standalone.mgmt;

import java.util.logging.Logger;

import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.wildfly.common.function.ExceptionRunnable;

/**
 * Test case to test resource limits and clean up of management interface connections.
 * <p>
 * This test case uses system properties for configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ManagementInterfaceResourcesTestCase extends AbstractManagementInterfaceResourcesTestCase {
    protected static final Logger LOG = Logger.getLogger(ManagementInterfaceResourcesTestCase.class.getName());

    /*
     * System Properties
     */
    private static final String BACKLOG_PROPERTY = "org.wildfly.management.backlog";
    private static final String CONNECTION_HIGH_WATER_PROPERTY = "org.wildfly.management.connection-high-water";
    private static final String CONNECTION_LOW_WATER_PROPERTY = "org.wildfly.management.connection-low-water";
    private static final String NO_REQUEST_TIMEOUT_PROPERTY = "org.wildfly.management.no-request-timeout";
    /*
     * Command Templates
     */
    private static final String SYSTEM_PROPERTY_ADD = "/system-property=%s:add(value=%d)";
    private static final String SYSTEM_PROPERTY_REMOVE = "/system-property=%s:remove()";

    protected void runTest(int noRequestTimeout, ExceptionRunnable<Exception> test) throws Exception {
        controller.start();
        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine(String.format(SYSTEM_PROPERTY_ADD, BACKLOG_PROPERTY, 2));
            cli.sendLine(String.format(SYSTEM_PROPERTY_ADD, CONNECTION_HIGH_WATER_PROPERTY, 6));
            cli.sendLine(String.format(SYSTEM_PROPERTY_ADD, CONNECTION_LOW_WATER_PROPERTY, 3));
            cli.sendLine(String.format(SYSTEM_PROPERTY_ADD, NO_REQUEST_TIMEOUT_PROPERTY, noRequestTimeout));
        }

        try {
            controller.reload();

            test.run();
        } finally {
            controller.reload();

            try (CLIWrapper cli = new CLIWrapper(true)) {
                cli.sendLine(String.format(SYSTEM_PROPERTY_REMOVE, BACKLOG_PROPERTY));
                cli.sendLine(String.format(SYSTEM_PROPERTY_REMOVE, CONNECTION_HIGH_WATER_PROPERTY));
                cli.sendLine(String.format(SYSTEM_PROPERTY_REMOVE, CONNECTION_LOW_WATER_PROPERTY));
                cli.sendLine(String.format(SYSTEM_PROPERTY_REMOVE, NO_REQUEST_TIMEOUT_PROPERTY));
            }
            controller.stop();
        }
    }

    @Override
    Logger log() {
        return LOG;
    }
}
