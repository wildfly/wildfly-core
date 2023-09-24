/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.profiles;

import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(AbstractLoggingProfilesTestCase.LoggingProfilesTestCaseSetup.class)
public class LoggingProfilesTestCase extends AbstractLoggingProfilesTestCase {

    public LoggingProfilesTestCase() {
        super(LoggingServiceActivator.class, 2);
    }
}
