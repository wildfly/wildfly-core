/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.management.client.content;

import static org.jboss.as.management.client.content.ManagedDMRContentTypeResourceDefinition.HASH;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.dmr.ModelNode;

/**
 * Add handler for a resource that represents the parent node for a tree of child resources each of
 * which represents a named bit of re-usable DMR.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagedDMRContentTypeAddHandler implements OperationStepHandler {

    private final ContentRepository contentRepository;
    private final HostFileRepository fileRepository;
    private final String childType;

    ManagedDMRContentTypeAddHandler(final ContentRepository contentRepository,
                                    final HostFileRepository fileRepository,
                                    final String childType) {
        this.contentRepository = contentRepository;
        this.fileRepository = fileRepository;
        this.childType = childType;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        PathAddress address = context.getCurrentAddress();
        byte[] hash = HASH.validateOperation(operation).asBytesOrNull();

        if (hash != null && fileRepository != null) {
            // Ensure the local repo has the files
            fileRepository.getDeploymentFiles(new ContentReference(address.toCLIStyleString(), hash));
        }

        // Create and add the specialized resource type we use for this resource tree
        ManagedDMRContentTypeResource resource = new ManagedDMRContentTypeResource(address, childType, hash, contentRepository);
        context.addResource(PathAddress.EMPTY_ADDRESS, resource);
    }
}
