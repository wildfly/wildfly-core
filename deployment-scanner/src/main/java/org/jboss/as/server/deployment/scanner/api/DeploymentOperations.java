/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deployment.scanner.api;

import java.io.Closeable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.jboss.dmr.ModelNode;

/**
 * Abstraction of the operations a deployment scanner needs to perform on the target
 * server.
 *
 * @author Stuart Douglas
 */
public interface DeploymentOperations extends Closeable {

    /**
     * Execute an operation to bring the server deployment model in sync with the current
     * scanned directory content.
     *
     * @param operation the operation to execute. Cannot be {@code null}
     * @param executorService executor that can be used to asynchronously execute the operation if the implementing
     *                          class does not wish to use its own executor
     * @return future from which the operation response can be obtained
     */
    Future<ModelNode> deploy(final ModelNode operation, final ExecutorService executorService);

    /**
     * Gets the enabled status of the deployments currently in the configuration model.
     *
     * @return map of deployment names to a boolean that will be {@code true} if the deployment is enabled.
     *         Map will not be {@code null}
     */
    Map<String, Boolean> getDeploymentsStatus();

    /** 
     * Gets the names of deployments currently in the configuration model that are not owned by {@code owner} 
     *
     * @param owner node of type LIST with elements of type PROPERTY whose data represents the address of the 
     *              management resource associated with the caller. Cannot be {@code null} or an empty list 
     *
     * @return a set of names of unrelated deployments. Will not be {@code null} 
     */
    Set<String> getUnrelatedDeployments(ModelNode owner);

    /**
     * Factory for creating a {@code DeploymentOperations} instance.
     */
    interface Factory {
        /**
         * Create a {@code DeploymentOperations} instance.
         * @return the instance. Will not be {@code null}
         */
        DeploymentOperations create();
    }

}
