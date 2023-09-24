/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.io.IOException;

import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public class ThreadsSubsystemTestCase extends AbstractSubsystemBaseTest {
    public ThreadsSubsystemTestCase() {
        super(ThreadsExtension.SUBSYSTEM_NAME, new ThreadsExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("threads-subsystem-2_0.xml");
    }

    // TODO WFCORE-1353 means this doesn't have to always fail now; consider just deleting this
//    @Override
//    protected void validateDescribeOperation(KernelServices hc, AdditionalInitialization serverInit, ModelNode expectedModel) throws Exception {
//        final ModelNode operation = createDescribeOperation();
//        final ModelNode result = hc.executeOperation(operation);
//        Assert.assertTrue("The subsystem describe operation must fail",
//                result.hasDefined(ModelDescriptionConstants.FAILURE_DESCRIPTION));
//    }

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
