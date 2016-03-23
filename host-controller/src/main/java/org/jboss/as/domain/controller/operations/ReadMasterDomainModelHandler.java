/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.mgmt.HostInfo;
import org.jboss.dmr.ModelNode;

/**
 * Step handler responsible for collecting a complete description of the domain model,
 * which is going to be sent back to a remote host-controller. This is called when the
 * remote slave boots up or when it reconnects to the DC
 *
 * @author John Bailey
 * @author Kabir Khan
 * @author Emanuel Muckenhuber
 */
public class ReadMasterDomainModelHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "read-master-domain-model";

    private final HostInfo hostInfo;
    private final Transformers transformers;
    private final ExtensionRegistry extensionRegistry;
    private final boolean lock;

    public ReadMasterDomainModelHandler(final HostInfo hostInfo, final Transformers transformers, final ExtensionRegistry extensionRegistry, boolean lock) {
        this.hostInfo = hostInfo;
        this.transformers = transformers;
        this.extensionRegistry = extensionRegistry;
        this.lock = lock;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // if the calling process has already acquired a lock, don't relock.
        if (lock) {
            context.acquireControllerLock();
        }

        final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry;
        final Resource resource = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
        // The host info is only null in the tests
        if (hostInfo == null) {
            ignoredTransformationRegistry = Transformers.DEFAULT;
        } else {
            final ReadMasterDomainModelUtil.RequiredConfigurationHolder rc = hostInfo.populateRequiredConfigurationHolder(resource, extensionRegistry);
            ignoredTransformationRegistry = ReadMasterDomainModelUtil.createHostIgnoredRegistry(hostInfo, rc);
        }

        final OperationStepHandler handler = new ReadDomainModelHandler(ignoredTransformationRegistry, transformers, lock);
        context.addStep(handler, OperationContext.Stage.MODEL);
    }

}
