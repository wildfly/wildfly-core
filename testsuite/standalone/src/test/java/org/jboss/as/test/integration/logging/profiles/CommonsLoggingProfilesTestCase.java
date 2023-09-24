/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.profiles;

import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerSetup;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
@ServerSetup(AbstractLoggingProfilesTestCase.LoggingProfilesTestCaseSetup.class)
public class CommonsLoggingProfilesTestCase extends AbstractLoggingProfilesTestCase {

    public CommonsLoggingProfilesTestCase() {
        super(CommonsLoggingServiceActivator.class, 2);
    }
}
