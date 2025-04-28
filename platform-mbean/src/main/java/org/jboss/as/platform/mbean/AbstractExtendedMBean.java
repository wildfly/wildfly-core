/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

/**
 * Utility to access to the com.sun.management MBeans
 *
 * @author jdenise
 */
class AbstractExtendedMBean {

    private final ObjectName name;

    AbstractExtendedMBean(String name) {
        try {
            this.name = new ObjectName(name);
        } catch (MalformedObjectNameException ex) {
            throw new IllegalStateException(ex);
        }
    }

    boolean isAttributeDefined(String attributeName) {
        Objects.requireNonNull(attributeName);
        try {
            for (MBeanAttributeInfo attribute
                    : ManagementFactory.getPlatformMBeanServer().getMBeanInfo(name).getAttributes()) {
                if (attributeName.equals(attribute.getName())) {
                    return true;
                }
            }
        } catch (InstanceNotFoundException | IntrospectionException | ReflectionException ex) {
            // XXX OK, attribute not available.
        }
        return false;
    }

    // Operation
    boolean isOperationDefined(String operationName, String[] types) {
        Objects.requireNonNull(operationName);
        Objects.requireNonNull(types);
        try {
            for (MBeanOperationInfo op
                    : ManagementFactory.getPlatformMBeanServer().getMBeanInfo(name).getOperations()) {
                if (operationName.equals(op.getName())) {
                    List<String> signature = new ArrayList<>();
                    for(MBeanParameterInfo p : op.getSignature()) {
                        signature.add(p.getType());
                    }
                    List<String> receivedTypes =  Arrays.asList(types);
                    if(receivedTypes.equals(signature)) {
                        return true;
                    }
                }
            }
        } catch (InstanceNotFoundException | IntrospectionException | ReflectionException ex) {
            // XXX OK, operation not available.
        }
        return false;
    }

    Object getAttribute(String attributeName) {
        try {
            return ManagementFactory.getPlatformMBeanServer().
                    getAttribute(name, attributeName);
        } catch (AttributeNotFoundException
                | InstanceNotFoundException
                | MBeanException
                | ReflectionException ex) {
            throw new IllegalStateException(ex);
        }
    }

    void setAttribute(String attributeName, Object value) {
        try {
            Attribute attribute = new Attribute(attributeName, value);
            ManagementFactory.getPlatformMBeanServer().
                    setAttribute(name, attribute);
        } catch (AttributeNotFoundException
                | InstanceNotFoundException
                | InvalidAttributeValueException
                | MBeanException
                | ReflectionException ex) {
            throw new IllegalStateException(ex);
        }
    }

    Object invokeOperation(String opName, Object[] values, String[] signature) {
        try {
            return ManagementFactory.getPlatformMBeanServer().
                    invoke(name, opName, values, signature);
        } catch (InstanceNotFoundException | MBeanException | ReflectionException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
