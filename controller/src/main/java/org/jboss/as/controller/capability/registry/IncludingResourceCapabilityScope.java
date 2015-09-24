/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.capability.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Base class for {@link CapabilityScope} implementations that use {@code includes} attributes
 * to indicate they include other resources of the same type.
 *
 * @author Brian Stansberry
 */
abstract class IncludingResourceCapabilityScope implements CapabilityScope {

    final CapabilityResolutionContext.AttachmentKey<Map<String, Set<CapabilityScope>>> attachmentKey;
    final String type;
    final String value;

    IncludingResourceCapabilityScope(CapabilityResolutionContext.AttachmentKey<Map<String, Set<CapabilityScope>>> attachmentKey, String type, String value) {
        this.attachmentKey = attachmentKey;
        this.type = type;
        this.value = value;
    }

    @Override
    public String getName() {
        return type + "=" + value;
    }

    @Override
    public Set<CapabilityScope> getIncludingScopes(CapabilityResolutionContext context) {
        Map<String, Set<CapabilityScope>> attached = context.getAttachment(attachmentKey);
        if (attached == null) {
            attached = new HashMap<>();
            Map<String, Set<String>> included = new HashMap<>();
            Set<Resource.ResourceEntry> children = context.getResourceRoot().getChildren(type);
            for (Resource.ResourceEntry resource : children) {
                String name = resource.getName();
                Set<String> includes = getIncludes(resource);
                included.put(name, includes);
                attached.put(name, new HashSet<>());
            }
            for (Resource.ResourceEntry resource : children) {
                String name = resource.getName();
                storeIncludes(createIncludedContext(name), name, attached, included);
            }
            context.attach(attachmentKey, attached);
        }

        Set<CapabilityScope> result = attached.get(value);
        return result == null ? Collections.emptySet() : result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getName() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IncludingResourceCapabilityScope that = (IncludingResourceCapabilityScope) o;

        return type.equals(that.type) && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    protected abstract CapabilityScope createIncludedContext(String name);

    private static Set<String> getIncludes(Resource.ResourceEntry resource) {
        Set<String> result;
        ModelNode model = resource.getModel();
        if (model.hasDefined(INCLUDES)) {
            result = new HashSet<>();
            for (ModelNode node : model.get(INCLUDES).asList()) {
                result.add(node.asString());
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }

    private static void storeIncludes(CapabilityScope includedContext, String key,
                               Map<String, Set<CapabilityScope>> attached,
                               Map<String, Set<String >> included) {
        for (String includer : included.get(key)) {
            if (!includedContext.getName().equals(includer)) { // guard against cycles
                Set<CapabilityScope> includees = attached.get(includer);
                if (includees != null) {
                    includees.add(includedContext);
                    // Continue up the chain
                    storeIncludes(includedContext, includer, attached, included);
                } // else 'includer' must have been bogus
            }
        }
    }
}
