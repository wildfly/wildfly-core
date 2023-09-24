/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanUtil.escapeMBeanName;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * Resource impl for the {@code java.lang.management.BufferPoolMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BufferPoolMXBeanResource extends AbstractPlatformMBeanResource {

    BufferPoolMXBeanResource() {
        super(PlatformMBeanConstants.BUFFER_POOL_PATH);
    }

    @Override
    ResourceEntry getChildEntry(String name) {
        if (getChildrenNames().contains(name)) {
            return new LeafPlatformMBeanResource(PathElement.pathElement(ModelDescriptionConstants.NAME, name));
        }
        return null;
    }

    @Override
    Set<String> getChildrenNames() {
        try {
            final Set<String> result = new LinkedHashSet<String>();
            final ObjectName pattern = new ObjectName(PlatformMBeanConstants.BUFFER_POOL_MXBEAN_DOMAIN_TYPE + ",name=*");
            Set<ObjectName> names = ManagementFactory.getPlatformMBeanServer().queryNames(pattern, null);
            for (ObjectName on : names) {
                result.add(escapeMBeanName(on.getKeyProperty(ModelDescriptionConstants.NAME)));
            }
            return result;
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> getChildTypes() {
        return Collections.singleton(ModelDescriptionConstants.NAME);
    }
}
