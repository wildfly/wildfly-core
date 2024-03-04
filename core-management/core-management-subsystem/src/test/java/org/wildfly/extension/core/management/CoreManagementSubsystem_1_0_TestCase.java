/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;

import java.io.IOException;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat Inc.
 */
public class CoreManagementSubsystem_1_0_TestCase extends AbstractSubsystemBaseTest {

    public CoreManagementSubsystem_1_0_TestCase() {
        super(CoreManagementExtension.SUBSYSTEM_NAME, new CoreManagementExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("core-management-subsystem-1_0.xml");
    }

    @Override
    public void testSubsystem() throws Exception {
        standardSubsystemTest(null, false);
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.ADMIN_ONLY_HC;
    }
}
