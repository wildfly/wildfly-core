/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import java.io.IOException;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;

/**
 * Security Manager version 3.0 subsystem tests.
 *
 * @author <a href="sguilhen@jboss.com">Stefan Guilhen</a>
 */
public class SecurityManagerSubsystemTestCase extends AbstractSubsystemBaseTest {

    public SecurityManagerSubsystemTestCase() {
        super(Constants.SUBSYSTEM_NAME, new SecurityManagerExtension());
    }


    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("security-manager-1.0.xml");
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return "schema/wildfly-security-manager_1_0.xsd";
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.ADMIN_ONLY_HC;
    }
}
