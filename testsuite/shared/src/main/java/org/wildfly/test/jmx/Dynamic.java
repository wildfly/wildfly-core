/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.jmx;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public class Dynamic implements DynamicMBean {
    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        return null; // not used in the test
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        // not used in the test
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        return null; // not used in the test
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        return null; // not used in the test
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        return null; // implementation not important for the test purposes
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return new MBeanInfo(
                Dynamic.class.getName(), "MBean for RBAC testing of JMX non-core MBeans", null, null,
                new MBeanOperationInfo[] {
                        new MBeanOperationInfo("helloReadOnly", "helloReadOnly", null, "void", MBeanOperationInfo.INFO),
                        new MBeanOperationInfo("helloWriteOnly", "helloWriteOnly", null, "void", MBeanOperationInfo.ACTION),
                        new MBeanOperationInfo("helloReadWrite", "helloReadWrite", null, "void", MBeanOperationInfo.ACTION_INFO),
                        new MBeanOperationInfo("helloUnknown", "helloUnknown", null, "void", MBeanOperationInfo.UNKNOWN)
                },
                null, null
        );
    }
}
