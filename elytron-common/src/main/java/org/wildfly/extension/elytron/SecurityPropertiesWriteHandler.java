/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

/**
 * @author Tomaz Cerar
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
class SecurityPropertiesWriteHandler extends AbstractWriteAttributeHandler {
    private final PropertiesAttributeDefinition securityProperties;
    private final String subsystemName;

    SecurityPropertiesWriteHandler(final String subsystemName, PropertiesAttributeDefinition attributeDefinition) {
        super(attributeDefinition);
        securityProperties = attributeDefinition;
        this.subsystemName = subsystemName;
    }

    private SecurityPropertyService getService(OperationContext context) {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);

        ServiceController<?> service = serviceRegistry.getService(SecurityPropertyService.serviceName(subsystemName));
        if (service != null) {
            Object serviceImplementation = service.getService();
            if (serviceImplementation != null && serviceImplementation instanceof SecurityPropertyService) {
                return (SecurityPropertyService) serviceImplementation;
            }
        }

        // Again should not be reachable.
        throw new IllegalStateException("Requires service not available or wrong type.");
    }

    private void setProperty(OperationContext context, String name, String value) {
        getService(context).setProperty(name, value);
    }

    private void removeProperty(OperationContext context, String name) {
        getService(context).removeProperty(name);
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder handbackHolder) throws OperationFailedException {
        Map<String, String> newProps = securityProperties.unwrap(context, resolvedValue);
        Map<String, String> oldProps = securityProperties.unwrap(context, currentValue);
        setProperties(context, newProps, oldProps);
        return false;
    }

    private void setProperties(final OperationContext context, Map<String, String> newProps, Map<String, String> oldProps) {
        Map<String, String> toRemove = new HashMap<>();
        Map<String, String> toAdd = new HashMap<>();
        Map<String, String> toUpdate = new HashMap<>();


        doDifference(newProps, oldProps, toAdd, toRemove, toUpdate);

        for (Map.Entry<String, String> entry : toAdd.entrySet()) {
            setProperty(context, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : toUpdate.entrySet()) {
            setProperty(context, entry.getKey(), entry.getValue());
        }
        for (String entry : toRemove.keySet()) {
            removeProperty(context, entry);
        }

    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Object handback) throws OperationFailedException {
        Map<String, String> newProps = securityProperties.unwrap(context, valueToRestore);
        Map<String, String> oldProps = securityProperties.unwrap(context, valueToRevert);
        setProperties(context, newProps, oldProps);
    }

    /**
     * calculate the difference of the two maps, so we know what was added, removed & updated
     * @param left
     * @param right
     * @param onlyOnLeft
     * @param onlyOnRight
     * @param updated
     */
    static void doDifference(
            Map<String, String> left,
            Map<String, String> right,
            Map<String, String> onlyOnLeft,
            Map<String, String> onlyOnRight,
            Map<String, String> updated
    ) {
        onlyOnRight.clear();
        onlyOnRight.putAll(right);
        for (Map.Entry<String, String> entry : left.entrySet()) {
            String leftKey = entry.getKey();
            String leftValue = entry.getValue();
            if (right.containsKey(leftKey)) {
                String rightValue = onlyOnRight.remove(leftKey);
                if (!leftValue.equals(rightValue)) {
                    updated.put(leftKey, leftValue);
                }
            } else {
                onlyOnLeft.put(leftKey, leftValue);
            }
        }
    }

}
