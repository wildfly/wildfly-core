/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.io.IOException;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.subsystem.test.AdditionalInitialization;

/**
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class ElytronSubsystem18_0TestCase extends AbstractElytronSubsystemBaseTest {

    public ElytronSubsystem18_0TestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("elytron-subsystem-18.0.xml");
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        // Our use of the expression=encryption resource requires kernel capability setup that TestEnvironment provides
        return new TestEnvironment(RunningMode.ADMIN_ONLY);
    }

}
