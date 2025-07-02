/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanConstants.GARBAGE_COLLECTOR_TYPE;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.TYPE;
import static org.jboss.as.platform.mbean.PlatformMBeanUtil.escapeMBeanName;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * Resource impl for the {@link GarbageCollectorMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class GarbageCollectorMXBeanResource extends AbstractPlatformMBeanResource {

    GarbageCollectorMXBeanResource() {
        super(PlatformMBeanConstants.GARBAGE_COLLECTOR_PATH);
    }

    @Override
    ResourceEntry getChildEntry(String name) {
        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (name.equals(escapeMBeanName(mbean.getName())) && mbean.getObjectName().getKeyProperty(TYPE).equals(GARBAGE_COLLECTOR_TYPE)) {
                return new LeafPlatformMBeanResource(PathElement.pathElement(ModelDescriptionConstants.NAME, name));
            }
        }
        return null;
    }

    @Override
    Set<String> getChildrenNames() {
        final Set<String> result = new LinkedHashSet<String>();
        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if(mbean.getObjectName().getKeyProperty(TYPE).equals(GARBAGE_COLLECTOR_TYPE)) {
                result.add(escapeMBeanName(mbean.getName()));
            }
        }
        return result;
    }

    @Override
    public Set<String> getChildTypes() {
        return Collections.singleton(ModelDescriptionConstants.NAME);
    }
}
