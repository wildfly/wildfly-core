/*
 * Copyright 2021 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;

import java.io.File;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.StartException;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;

/**
 * The {@code Doohickey} is the central point for resource initialisation allowing a resource to be
 * initialised for it's runtime API, as a service or a combination of them both.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class ElytronDoohickey<T> implements ExceptionFunction<OperationContext, T, OperationFailedException> {

    private final PathAddress resourceAddress;

    private volatile boolean modelResolved = false;
    private volatile ExceptionSupplier<T, StartException> serviceValueSupplier;

    private volatile T value;

    protected ElytronDoohickey(final PathAddress resourceAddress) {
        this.resourceAddress = checkNotNullParam("resourceAddress", resourceAddress);
    }

    @Override
    public final T apply(final OperationContext foreignContext) throws OperationFailedException {
        // The apply method is assumed to be called from the runtime API - i.e. MSC dependencies are not available.
        if (value == null) {
            synchronized(this) {
                if (value == null) {
                    resolveRuntime(foreignContext);
                    value = createImmediately(foreignContext);
                }
            }
        }

        return value;
    }

    public final T get() throws StartException {
        // The get method is assumed to be called as part of the MSC lifecycle.
        if (value == null) {
            synchronized(this) {
                if (value == null) {
                    value = serviceValueSupplier.get();
                }
            }
        }
        return value;
    }

    public final void prepareService(OperationContext context, CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {
        // If we know we have been initialised i.e. an early expression resolution do we want to skip the service wiring?
        serviceValueSupplier = prepareServiceSupplier(context, serviceBuilder);
    }

    public final void resolveRuntime(final OperationContext foreignContext) throws OperationFailedException {
        // The OperationContext may not be foreign but treat it as though it is.
        if (modelResolved == false) {
            synchronized(this) {
                if (modelResolved == false) {
                    ModelNode model = foreignContext.readResourceFromRoot(resourceAddress).getModel();
                    resolveRuntime(model, foreignContext);
                    modelResolved = true;
                }
            }
        }
    }

    protected File resolveRelativeToImmediately(String path, String relativeTo, OperationContext foreignContext) {
        PathResolver pathResolver = pathResolver();
        pathResolver.path(path);
        if (relativeTo != null) {
            PathManager pathManager = (PathManager) foreignContext.getServiceRegistry(false)
                    .getRequiredService(PathManagerService.SERVICE_NAME).getValue();
            pathResolver.relativeTo(relativeTo, pathManager);
        }
        File resolved = pathResolver.resolve();
        pathResolver.clear();

        return resolved;
    }

    protected abstract void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException;

    protected abstract ExceptionSupplier<T, StartException> prepareServiceSupplier(OperationContext context, CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException;

    protected abstract T createImmediately(OperationContext foreignContext) throws OperationFailedException;

}