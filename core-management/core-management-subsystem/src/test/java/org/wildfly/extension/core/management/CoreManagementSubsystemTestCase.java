/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import java.io.IOException;

import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat Inc.
 */
public class CoreManagementSubsystemTestCase extends AbstractSubsystemBaseTest {

    public CoreManagementSubsystemTestCase() {
        super(CoreManagementExtension.SUBSYSTEM_NAME, new CoreManagementExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("core-management-subsystem-1_0.xml");
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new AdditionalInitialization() {

            @Override
            protected ProcessType getProcessType() {
                return ProcessType.HOST_CONTROLLER;
            }

            @Override
            protected RunningMode getRunningMode() {
                return RunningMode.ADMIN_ONLY;
            }
        };
    }
}
