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

package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.dmr.ModelNode;

/**
 * Handler reading the transformed domain model.
 *
 * @author Emanuel Muckenhuber
 */
class ReadDomainModelHandler implements OperationStepHandler {

    private final Transformers transformers;
    private final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry;
    private final boolean lock;

    public ReadDomainModelHandler(final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry, final Transformers transformers, final boolean lock) {
        this.transformers = transformers;
        this.ignoredTransformationRegistry = ignoredTransformationRegistry != null ? ignoredTransformationRegistry : Transformers.DEFAULT;
        this.lock = lock;
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // if the calling process has already acquired a lock, don't relock.
        if (lock) {
            // Acquire the lock to make sure that nobody can modify the model before the slave has applied it
            context.acquireControllerLock();
        }

        final Transformers.TransformationInputs transformationInputs = new Transformers.TransformationInputs(context);
        final ReadMasterDomainModelUtil readUtil = ReadMasterDomainModelUtil.readMasterDomainResourcesForInitialConnect(transformers,
                transformationInputs, ignoredTransformationRegistry, transformationInputs.getRootResource());
        context.getResult().set(readUtil.getDescribedResources());
    }

}
