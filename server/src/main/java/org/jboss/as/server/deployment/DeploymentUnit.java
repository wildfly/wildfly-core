/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deployment;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * The deployment unit.  This object retains data which is persistent for the life of the
 * deployment.
 */
public interface DeploymentUnit extends Attachable {

    /**
     * Get the service name of the root deployment unit service.
     *
     * @return the service name
     */
    ServiceName getServiceName();

    /**
     * Get the deployment unit of the parent (enclosing) deployment.
     *
     * @return the parent deployment unit, or {@code null} if this is a top-level deployment
     */
    DeploymentUnit getParent();

    /**
     * Get the simple name of the deployment unit.
     *
     * @return the simple name
     */
    String getName();

    /**
     * Get the service registry.
     *
     * @return the service registry
     */
    ServiceRegistry getServiceRegistry();

    /**
     * Get or create the extension deployment model root.
     *
     * @param subsystemName the subsystem name.
     * @return the model
     */
    ModelNode getDeploymentSubsystemModel(final String subsystemName);

    /**
     * Create a management sub-model for components from the deployment itself. Operations, metrics and descriptions
     * have to be registered as part of the subsystem registration {@link org.jboss.as.controller.ExtensionContext} and
     * {@link org.jboss.as.controller.SubsystemRegistration#registerDeploymentModel(org.jboss.as.controller.ResourceDefinition)}.
     *
     * <p>
     * If the address is {@code null} only the subsystem resource will be created. If the address is not {@code null}
     * the subsystem resource will be created if it does not already exist and the resource for the address will also be
     * created.
     * </p>
     *
     * @param subsystemName the subsystem name for model
     * @param address the path address this sub-model should be created in or {@code null} to create the root subsystem resource
     * @return the model for the created resource
     *
     * @throws java.lang.IllegalStateException if a resource with the address has already been created
     */
    ModelNode createDeploymentSubModel(final String subsystemName, final PathElement address);

    /**
     * Create a management sub-model for components from the deployment itself. Operations, metrics and descriptions
     * have to be registered as part of the subsystem registration {@link org.jboss.as.controller.ExtensionContext} and
     * {@link org.jboss.as.controller.SubsystemRegistration#registerDeploymentModel(org.jboss.as.controller.ResourceDefinition)}.
     *
     * <p>
     * If the address is {@linkplain org.jboss.as.controller.PathAddress#EMPTY_ADDRESS} only the subsystem resource will
     * be created.
     * </p>
     *
     * <p>
     * If the address is not {@linkplain org.jboss.as.controller.PathAddress#EMPTY_ADDRESS} the subsystem resource as
     * well as any other child resources will be created if they do not exist. Finally an attempt will be made to create
     * the {@link org.jboss.as.controller.PathAddress#getLastElement() last} resource from the address.
     * </p>
     *
     * @param subsystemName the subsystem name the model was registered
     * @param address the path address this sub-model should be created in or {@link org.jboss.as.controller.PathAddress#EMPTY_ADDRESS}
     *                to create the root subsystem resource
     * @return the model for the created resource
     *
     * @see #createDeploymentSubModel(String, org.jboss.as.controller.PathElement)
     *
     * @throws java.lang.IllegalStateException if the {@link org.jboss.as.controller.PathAddress#getLastElement() last} resource already exists
     */
    ModelNode createDeploymentSubModel(final String subsystemName, final PathAddress address);

    /**
     * Create a management sub-model for components from the deployment itself. Operations, metrics and descriptions
     * have to be registered as part of the subsystem registration {@link org.jboss.as.controller.ExtensionContext} and
     * {@link org.jboss.as.controller.SubsystemRegistration#registerDeploymentModel(org.jboss.as.controller.ResourceDefinition)}.
     *
     * <p>
     * If the address is {@linkplain org.jboss.as.controller.PathAddress#EMPTY_ADDRESS} and the resource parameter is
     * {@code null} the subsystem resource be created. If the resource parameter is not {@code null} the resource will
     * be registered as the subsystem resource.
     * </p>
     *
     * <p>
     * If the address is not {@linkplain org.jboss.as.controller.PathAddress#EMPTY_ADDRESS} the subsystem resource as
     * well as any other child resources will be created if they do not exist. Finally an attempt will be made to create
     * the {@link org.jboss.as.controller.PathAddress#getLastElement() last} resource from the address if the resource
     * parameter is {@code null}. If the resource parameter is not {@code null} the resource parameter will be
     * registered as the {@link org.jboss.as.controller.PathAddress#getLastElement() last} resource.
     * </p>
     *
     * @param subsystemName the subsystem name the model was registered
     * @param address the path address this sub-model should be created in or {@link org.jboss.as.controller.PathAddress#EMPTY_ADDRESS}
     *                to create the root subsystem resource
     * @param resource the resource to be registered as sub-module or {@code null} to create the resource(s) from the address
     * @return the model for the optionally created resource
     *
     * @see #createDeploymentSubModel(String, org.jboss.as.controller.PathElement)
     *
     * @throws java.lang.IllegalStateException if the {@link org.jboss.as.controller.PathAddress#getLastElement() last} resource already exists
     */
    ModelNode createDeploymentSubModel(final String subsystemName, final PathAddress address, final Resource resource);

}
