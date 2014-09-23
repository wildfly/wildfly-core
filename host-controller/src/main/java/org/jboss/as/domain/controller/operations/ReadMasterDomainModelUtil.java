/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.jboss.as.domain.controller.operations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.dmr.ModelNode;

/**
 * Utility for the DC operation handlers to describe the missing resources for the slave hosts which are
 * set up to ignore domain config which does not affect their servers
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ReadMasterDomainModelUtil {

    public static final String DOMAIN_RESOURCE_ADDRESS = "domain-resource-address";

    public static final String DOMAIN_RESOURCE_MODEL = "domain-resource-model";

    private final Set<PathElement> newRootResources = new HashSet<>();

    private volatile List<ModelNode> describedResources;

    private ReadMasterDomainModelUtil() {
    }

    /**
     * Used to read the domain model when a slave host connects to the DC
     *
     *  @param context the operation context
     *  @param transformers the transformers for the host
     *  @param domainRoot the domain root resource
     *  @return a read master domain model util instance
     */
    static ReadMasterDomainModelUtil readMasterDomainResourcesForInitialConnect(
            final OperationContext context, final Transformers transformers, final Resource domainRoot, final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) throws OperationFailedException {

        Resource transformedResource = transformers.transformRootResource(context, domainRoot, ignoredTransformationRegistry);
        ReadMasterDomainModelUtil util = new ReadMasterDomainModelUtil();
        util.describedResources = util.describeAsNodeList(PathAddress.EMPTY_ADDRESS, transformedResource, false);
        return util;
    }

    /**
     * Gets a list of the resources for the slave's ApplyXXXXHandlers. Although the format might appear
     * similar as the operations generated at boot-time this description is only useful
     * to create the resource tree and cannot be used to invoke any operation.
     *
     * @return the resources
     */
    public List<ModelNode> getDescribedResources(){
        return describedResources;
    }

    /**
     * Describe the model as a list of resources with their address and model, which
     * the HC can directly apply to create the model. Although the format might appear
     * similar as the operations generated at boot-time this description is only useful
     * to create the resource tree and cannot be used to invoke any operation.
     *
     * @param rootAddress the address of the root resource being described
     * @param resource the root resource
     * @return the list of resources
     */
    private List<ModelNode> describeAsNodeList(PathAddress rootAddress, final Resource resource, boolean isRuntimeChange) {
        final List<ModelNode> list = new ArrayList<ModelNode>();

        describe(rootAddress, resource, list, isRuntimeChange);
        return list;
    }

    private void describe(final PathAddress base, final Resource resource, List<ModelNode> nodes, boolean isRuntimeChange) {
        if (resource.isProxy() || resource.isRuntime()) {
            return; // ignore runtime and proxies
        } else if (base.size() >= 1 && base.getElement(0).getKey().equals(ModelDescriptionConstants.HOST)) {
            return; // ignore hosts
        }
        if (base.size() == 1) {
            newRootResources.add(base.getLastElement());
        }
        final ModelNode description = new ModelNode();
        description.get(ReadMasterDomainModelUtil.DOMAIN_RESOURCE_ADDRESS).set(base.toModelNode());
        description.get(ReadMasterDomainModelUtil.DOMAIN_RESOURCE_MODEL).set(resource.getModel());
        nodes.add(description);
        for (final String childType : resource.getChildTypes()) {
            for (final Resource.ResourceEntry entry : resource.getChildren(childType)) {
                describe(base.append(entry.getPathElement()), entry, nodes, isRuntimeChange);
            }
        }
    }
}
