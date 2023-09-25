/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.AUTO_DEPLOY_EXPLODED;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.AUTO_DEPLOY_XML;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.AUTO_DEPLOY_ZIPPED;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.DEPLOYMENT_TIMEOUT;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.RUNTIME_FAILURE_CAUSES_ROLLBACK;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.SCAN_ENABLED;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.SCAN_INTERVAL;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.deployment.scanner.api.DeploymentScanner;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Class for write-attribute handlers that change an installed {@code DeploymentScanner}.
 *
 * @author Brian Stansberry
 */
final class UpdateScannerWriteAttributeHandler extends AbstractWriteAttributeHandler<DeploymentScanner> {

    UpdateScannerWriteAttributeHandler() {
        super(AUTO_DEPLOY_EXPLODED, AUTO_DEPLOY_XML, AUTO_DEPLOY_ZIPPED, DEPLOYMENT_TIMEOUT,
                RUNTIME_FAILURE_CAUSES_ROLLBACK, SCAN_ENABLED, SCAN_INTERVAL);
    }

    @Override
    protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation,
            final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue,
            final HandbackHolder<DeploymentScanner> handbackHolder) throws OperationFailedException {

        final String name = context.getCurrentAddressValue();
        final ServiceController<?> controller = context.getServiceRegistry(true).getRequiredService(DeploymentScannerService.getServiceName(name));
        if (controller.getState() == ServiceController.State.UP) {// https://issues.jboss.org/browse/WFCORE-1635
            DeploymentScanner scanner = (DeploymentScanner) controller.getValue();
            updateScanner(attributeName, scanner, resolvedValue);
            handbackHolder.setHandback(scanner);
        }

        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                         ModelNode valueToRestore, ModelNode valueToRevert, DeploymentScanner handback) throws OperationFailedException {
        if (handback != null) {
            updateScanner(attributeName, handback, context.resolveExpressions(valueToRestore));
        }
    }

    private void updateScanner(String attributeName, DeploymentScanner scanner, ModelNode resolvedNewValue) {
        AttributeDefinition ad = getAttributeDefinition(attributeName);
        if (ad == AUTO_DEPLOY_EXPLODED) {
            scanner.setAutoDeployExplodedContent(resolvedNewValue.asBoolean());
        } else if (ad == AUTO_DEPLOY_XML) {
            scanner.setAutoDeployXMLContent(resolvedNewValue.asBoolean());
        } else if (ad == AUTO_DEPLOY_ZIPPED) {
            scanner.setAutoDeployZippedContent(resolvedNewValue.asBoolean());
        } else if (ad == DEPLOYMENT_TIMEOUT) {
            scanner.setDeploymentTimeout(resolvedNewValue.asLong());
        } else if (ad == RUNTIME_FAILURE_CAUSES_ROLLBACK) {
            scanner.setRuntimeFailureCausesRollback(resolvedNewValue.asBoolean());
        } else if (ad == SCAN_INTERVAL) {
            scanner.setScanInterval(resolvedNewValue.asInt());
        } else if (ad == SCAN_ENABLED) {
            boolean enable = resolvedNewValue.asBoolean();
            if (enable) {
                scanner.startScanner();
            }
            else {
                scanner.stopScanner();
            }
        } else {
            // Someone forgot something
            throw new IllegalStateException();
        }
    }
}
