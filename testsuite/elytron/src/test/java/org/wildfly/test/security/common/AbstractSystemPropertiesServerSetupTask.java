/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 * {@link ServerSetupTask} instance for system properties setup.
 *
 * @author Josef Cacek
 */
public abstract class AbstractSystemPropertiesServerSetupTask implements ServerSetupTask {

    private static final Logger LOGGER = Logger.getLogger(AbstractSystemPropertiesServerSetupTask.class);

    private Map<String, String> systemProperties;

    // Public methods --------------------------------------------------------

    public final void setup(final ManagementClient managementClient) throws Exception {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.trace("Adding system properties.");
        }
        systemProperties = getSystemProperties();
        if (systemProperties == null || systemProperties.isEmpty()) {
            LOGGER.warn("No system property configured in the ServerSetupTask");
            return;
        }

        final List<ModelNode> updates = new ArrayList<ModelNode>();

        for (Map.Entry<String, String> systemProperty : systemProperties.entrySet()) {
            final String propertyName = systemProperty.getKey();
            if (propertyName == null || propertyName.trim().length() == 0) {
                LOGGER.warn("Empty property name provided.");
                continue;
            }
            ModelNode op = new ModelNode();
            op.get(OP).set(ADD);
            op.get(OP_ADDR).add(SYSTEM_PROPERTY, propertyName);
            op.get(ModelDescriptionConstants.VALUE).set(systemProperty.getValue());
            updates.add(op);
        }
        CoreUtils.applyUpdates(updates, managementClient.getControllerClient());
    }

    /**
     *
     * @param managementClient
     * @param containerId
     * @see org.jboss.as.test.integration.security.common.AbstractSecurityDomainSetup#tearDown(org.jboss.as.arquillian.container.ManagementClient,
     *      java.lang.String)
     */
    public final void tearDown(ManagementClient managementClient) throws Exception {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.trace("Removing system properties.");
        }
        if (systemProperties == null || systemProperties.isEmpty()) {
            return;
        }

        final List<ModelNode> updates = new ArrayList<ModelNode>();

        for (Map.Entry<String, String> systemProperty : systemProperties.entrySet()) {
            final String propertyName = systemProperty.getKey();
            if (propertyName == null || propertyName.trim().length() == 0) {
                continue;
            }
            ModelNode op = new ModelNode();
            op.get(OP).set(REMOVE);
            op.get(OP_ADDR).add(SYSTEM_PROPERTY, propertyName);
            updates.add(op);
        }
        CoreUtils.applyUpdates(updates, managementClient.getControllerClient());

    }


    // Protected methods -----------------------------------------------------

    /**
     * Returns configuration of the login modules.
     *
     * @return
     */
    protected abstract Map<String, String> getSystemProperties();

}
