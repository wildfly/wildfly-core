/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUDIT_LOG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_MEMORY_HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOGGER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_HISTORY;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class InMemoryAuditReportSetupTask implements ServerSetupTask {

    static final String CONFIGURATION_CHANGE_NAME = "changes";
    static final PathAddress AUDIT_LOG_ADDRESS = PathAddress.pathAddress().append(CORE_SERVICE, MANAGEMENT).append(ACCESS, AUDIT);
    static final PathAddress AUDIT_LOG_LOGGER_ADDR = AUDIT_LOG_ADDRESS.append(LOGGER, AUDIT_LOG);
    static final PathAddress IN_MEMORY_HANDLER_ADDR = AUDIT_LOG_ADDRESS.append(IN_MEMORY_HANDLER, CONFIGURATION_CHANGE_NAME);
    static final PathAddress AUDIT_LOG_LOGGER_IN_MEMORY_HANDLER_ADDR = AUDIT_LOG_LOGGER_ADDR.append(HANDLER, CONFIGURATION_CHANGE_NAME);

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        ModelNode createHandler = Util.createAddOperation(IN_MEMORY_HANDLER_ADDR);
        createHandler.get(MAX_HISTORY).set(3);
        managementClient.getControllerClient().execute(createHandler);
        ModelNode activateHandler = Util.createAddOperation(AUDIT_LOG_LOGGER_IN_MEMORY_HANDLER_ADDR);
        managementClient.getControllerClient().execute(activateHandler);
        ModelNode enableAuditLog = Util.getWriteAttributeOperation(AUDIT_LOG_LOGGER_ADDR, ENABLED, true);
        managementClient.getControllerClient().execute(enableAuditLog);
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        ModelNode disableAuditLog = Util.getWriteAttributeOperation(AUDIT_LOG_LOGGER_ADDR, ENABLED, false);
        managementClient.getControllerClient().execute(disableAuditLog);
        ModelNode deactivateHandler = Util.createRemoveOperation(AUDIT_LOG_LOGGER_IN_MEMORY_HANDLER_ADDR);
        managementClient.getControllerClient().execute(deactivateHandler);
        ModelNode removeHandler = Util.createRemoveOperation(IN_MEMORY_HANDLER_ADDR);
        managementClient.getControllerClient().execute(removeHandler);
    }

}
