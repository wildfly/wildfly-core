/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBDEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Support for creation of resources on deployments or retrieving the model of a resource on deployments.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public final class DeploymentResourceSupport {
    static final AttachmentKey<Resource> DEPLOYMENT_RESOURCE = AttachmentKey.create(Resource.class);
    static final AttachmentKey<ImmutableManagementResourceRegistration> REGISTRATION_ATTACHMENT = AttachmentKey.create(ImmutableManagementResourceRegistration.class);
    static final AttachmentKey<ManagementResourceRegistration> MUTABLE_REGISTRATION_ATTACHMENT = AttachmentKey.create(ManagementResourceRegistration.class);

    private final DeploymentUnit deploymentUnit;

    protected DeploymentResourceSupport(final DeploymentUnit deploymentUnit) {
        this.deploymentUnit = deploymentUnit;
    }

    /**
     * Checks to see if a subsystem resource has already been registered for the deployment.
     *
     * @param subsystemName the name of the subsystem
     *
     * @return {@code true} if the subsystem exists on the deployment otherwise {@code false}
     */
    public boolean hasDeploymentSubsystemModel(final String subsystemName) {
        final Resource root = deploymentUnit.getAttachment(DEPLOYMENT_RESOURCE);
        final PathElement subsystem = PathElement.pathElement(SUBSYSTEM, subsystemName);
        return root.hasChild(subsystem);
    }

    /**
     * Get the subsystem deployment model root.
     *
     * <p>
     * If the subsystem resource does not exist one will be created.
     * </p>
     *
     * @param subsystemName the subsystem name.
     *
     * @return the model
     */
    public ModelNode getDeploymentSubsystemModel(final String subsystemName) {
        assert subsystemName != null : "The subsystemName cannot be null";
        return getDeploymentSubModel(subsystemName, PathAddress.EMPTY_ADDRESS, null, deploymentUnit);
    }

    /**
     * Registers the resource to the parent deployment resource. The model returned is that of the resource parameter.
     *
     * @param subsystemName the subsystem name
     * @param resource      the resource to be used for the subsystem on the deployment
     *
     * @return the model
     *
     * @throws java.lang.IllegalStateException if the subsystem resource already exists
     */
    public ModelNode registerDeploymentSubsystemResource(final String subsystemName, final Resource resource) {
        assert subsystemName != null : "The subsystemName cannot be null";
        assert resource != null : "The resource cannot be null";
        return registerDeploymentSubResource(subsystemName, PathAddress.EMPTY_ADDRESS, resource);
    }

    /**
     * Checks to see if a resource has already been registered for the specified address on the subsystem.
     *
     * @param subsystemName the name of the subsystem
     * @param address       the address to check
     *
     * @return {@code true} if the address exists on the subsystem otherwise {@code false}
     */
    public boolean hasDeploymentSubModel(final String subsystemName, final PathElement address) {
        final Resource root = deploymentUnit.getAttachment(DEPLOYMENT_RESOURCE);
        final PathElement subsystem = PathElement.pathElement(SUBSYSTEM, subsystemName);
        return root.hasChild(subsystem) && (address == null || root.getChild(subsystem).hasChild(address));
    }

    /**
     * Checks to see if a resource has already been registered for the specified address on the subsystem.
     *
     * @param subsystemName the name of the subsystem
     * @param address       the address to check
     *
     * @return {@code true} if the address exists on the subsystem otherwise {@code false}
     */
    public boolean hasDeploymentSubModel(final String subsystemName, final PathAddress address) {
        final Resource root = deploymentUnit.getAttachment(DEPLOYMENT_RESOURCE);
        final PathElement subsystem = PathElement.pathElement(SUBSYSTEM, subsystemName);
        boolean found = false;
        if (root.hasChild(subsystem)) {
            if (address == PathAddress.EMPTY_ADDRESS) {
                return true;
            }
            Resource parent = root.getChild(subsystem);
            for (PathElement child : address) {
                if (parent.hasChild(child)) {
                    found = true;
                    parent = parent.getChild(child);
                } else {
                    found = false;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Gets the sub-model for a components from the deployment itself. Operations, metrics and descriptions have to be
     * registered as part of the subsystem registration {@link org.jboss.as.controller.ExtensionContext} and
     * {@link org.jboss.as.controller.SubsystemRegistration#registerDeploymentModel(org.jboss.as.controller.ResourceDefinition)}.
     *
     * <p>
     * If the subsystem resource does not exist it will be created. If no resource exists for the address parameter on
     * the resource it also be created.
     * </p>
     *
     * @param subsystemName the name of the subsystem
     * @param address       the path address this sub-model should return the model for
     *
     * @return the model for the resource
     */
    public ModelNode getDeploymentSubModel(final String subsystemName, final PathElement address) {
        assert subsystemName != null : "The subsystemName cannot be null";
        return getDeploymentSubModel(subsystemName, address, deploymentUnit);
    }

    /**
     * Gets the sub-model for a components from the deployment itself. Operations, metrics and descriptions have to be
     * registered as part of the subsystem registration {@link org.jboss.as.controller.ExtensionContext} and
     * {@link org.jboss.as.controller.SubsystemRegistration#registerDeploymentModel(org.jboss.as.controller.ResourceDefinition)}.
     *
     * <p>
     * The subsystem resource as well as each {@link org.jboss.as.controller.PathAddress#getParent()} parent element}
     * from the address will be created if it does not already exist.
     * </p>
     *
     * @param subsystemName the name of the subsystem
     * @param address       the path address this sub-model should return the model for
     *
     * @return the model for the resource
     */
    public ModelNode getDeploymentSubModel(final String subsystemName, final PathAddress address) {
        assert subsystemName != null : "The subsystemName cannot be null";
        assert address != null : "The address cannot be null";
        return getDeploymentSubModel(subsystemName, address, null, deploymentUnit);
    }

    /**
     * Registers the provided resource as the resource for the {@link org.jboss.as.controller.PathAddress#getLastElement()
     * last element} of the address. Operations, metrics and descriptions have to be registered as part of the
     * subsystem registration {@link org.jboss.as.controller.ExtensionContext} and {@link
     * org.jboss.as.controller.SubsystemRegistration#registerDeploymentModel(org.jboss.as.controller.ResourceDefinition)}.
     *
     * <p>
     * The subsystem resource as well as each {@link org.jboss.as.controller.PathAddress#getParent()} parent element}
     * from the address will be created if it does not already exist.
     * </p>
     *
     * @param subsystemName the subsystem name the model was registered
     * @param address       the path address this sub-model should be created in
     * @param resource      the resource to be registered as sub-module
     *
     * @return the {@link org.jboss.as.controller.registry.Resource#getModel() model} from the resource parameter
     *
     * @throws java.lang.IllegalStateException if the {@link org.jboss.as.controller.PathAddress#getLastElement() last}
     *                                         resource already exists
     */
    public ModelNode registerDeploymentSubResource(final String subsystemName, final PathAddress address, final Resource resource) {
        final Resource root = deploymentUnit.getAttachment(DEPLOYMENT_RESOURCE);
        synchronized (root) {
            final ImmutableManagementResourceRegistration registration = deploymentUnit.getAttachment(REGISTRATION_ATTACHMENT);
            final PathElement subsystemPath = PathElement.pathElement(SUBSYSTEM, subsystemName);
            if (address == PathAddress.EMPTY_ADDRESS) {
                return register(root, subsystemPath, resource).getModel();
            }
            Resource parent = getOrCreate(root, subsystemPath);
            int count = address.size() - 1;
            for (int index = 0; index < count; index++) {
                parent = getOrCreate(parent, address.getElement(index));
            }
            final ImmutableManagementResourceRegistration subModel = registration.getSubModel(getSubsystemAddress(subsystemName, address));
            if (subModel == null) {
                throw new IllegalStateException(address.toString());
            }
            return register(parent, address.getLastElement(), resource).getModel();
        }
    }

    /**
     * Gets or creates the a resource for the sub-deployment on the parent deployments resource.
     *
     * @param deploymentName the name of the deployment
     * @param parent         the parent deployment used to find the parent resource
     *
     * @return the already registered resource or a newly created resource
     */
    static Resource getOrCreateSubDeployment(final String deploymentName, final DeploymentUnit parent) {
        final Resource root = parent.getAttachment(DEPLOYMENT_RESOURCE);
        return getOrCreate(root, PathElement.pathElement(SUBDEPLOYMENT, deploymentName));
    }

    /**
     * Cleans up the subsystem children for the deployment and each sub-deployment resource.
     *
     * @param resource the subsystem resource to clean up
     */
    static void cleanup(final Resource resource) {
        synchronized (resource) {
            for (final Resource.ResourceEntry entry : resource.getChildren(SUBSYSTEM)) {
                resource.removeChild(entry.getPathElement());
            }
            for (final Resource.ResourceEntry entry : resource.getChildren(SUBDEPLOYMENT)) {
                resource.removeChild(entry.getPathElement());
            }
        }
    }

    /**
     * @see #getDeploymentSubModel(String, org.jboss.as.controller.PathElement, DeploymentUnit)
     */
    static ModelNode getDeploymentSubModel(final String subsystemName, final PathElement address, final DeploymentUnit unit) {
        final Resource root = unit.getAttachment(DEPLOYMENT_RESOURCE);
        synchronized (root) {
            final ImmutableManagementResourceRegistration registration = unit.getAttachment(REGISTRATION_ATTACHMENT);
            final PathElement subsystemPath = PathElement.pathElement(SUBSYSTEM, subsystemName);
            if (address == null) {
                return getOrCreate(root, subsystemPath, null).getModel();
            }
            Resource parent = getOrCreate(root, subsystemPath);
            final ImmutableManagementResourceRegistration subModel = registration.getSubModel(PathAddress.pathAddress(subsystemPath, address));
            if (subModel == null) {
                throw new IllegalStateException(address.toString());
            }
            return getOrCreate(parent, address, null).getModel();
        }
    }

    /**
     * @see #getDeploymentSubModel(String, org.jboss.as.controller.PathAddress)
     */
    static ModelNode getDeploymentSubModel(final String subsystemName, final PathAddress address, final Resource resource, final DeploymentUnit unit) {
        final Resource root = unit.getAttachment(DEPLOYMENT_RESOURCE);
        synchronized (root) {
            final ImmutableManagementResourceRegistration registration = unit.getAttachment(REGISTRATION_ATTACHMENT);
            final PathElement subsystemPath = PathElement.pathElement(SUBSYSTEM, subsystemName);
            if (address == PathAddress.EMPTY_ADDRESS) {
                return getOrCreate(root, subsystemPath, resource).getModel();
            }
            Resource parent = getOrCreate(root, subsystemPath);
            int count = address.size() - 1;
            for (int index = 0; index < count; index++) {
                parent = getOrCreate(parent, address.getElement(index));
            }
            final ImmutableManagementResourceRegistration subModel = registration.getSubModel(getSubsystemAddress(subsystemName, address));
            if (subModel == null) {
                throw new IllegalStateException(address.toString());
            }
            return getOrCreate(parent, address.getLastElement(), resource).getModel();
        }
    }

    private static Resource getOrCreate(final Resource parent, final PathElement element) {
        return getOrCreate(parent, element, null);
    }

    private static Resource getOrCreate(final Resource parent, final PathElement element, final Resource desired) {
        synchronized (parent) {
            if (parent.hasChild(element)) {
                if (desired == null) {
                    return parent.requireChild(element);
                } else {
                    throw new IllegalStateException();
                }
            } else {
                return register(parent, element, desired);
            }
        }
    }

    private static Resource register(final Resource parent, final PathElement element, final Resource desired) {
        synchronized (parent) {
            Resource toRegister = desired;
            if (toRegister == null) {
                toRegister = Resource.Factory.create(true);
            } else if (!toRegister.isRuntime()) {
                throw ControllerLogger.ROOT_LOGGER.deploymentResourceMustBeRuntimeOnly();
            }
            parent.registerChild(element, toRegister);
            return toRegister;
        }
    }

    private static PathAddress getSubsystemAddress(final String subsystemName, final PathAddress elements) {
        PathAddress address = PathAddress.EMPTY_ADDRESS.append(PathElement.pathElement(SUBSYSTEM, subsystemName));
        address = address.append(elements);
        return address;
    }

}
