/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import org.jboss.as.controller.RequirementServiceTarget;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.service.ServiceDependency;

/**
 * The deployment unit processor context.  Maintains state pertaining to the current cycle
 * of deployment/undeployment.  This context object will be discarded when processing is
 * complete; data which must persist for the life of the deployment should be attached to
 * the {@link DeploymentUnit}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface DeploymentPhaseContext extends Attachable {

    /**
     * Get the service name of the current deployment phase.
     *
     * @return the deployment phase service name
     */
    ServiceName getPhaseServiceName();

    /**
     * Get the service target into which this phase should install services.  <b>Please note</b> that
     * services added via this context do <b>not</b> have any implicit dependencies by default; any root-level
     * deployment services that you add should depend on the service name of the current phase, acquired via
     * the {@link #getPhaseServiceName()} method.
     *
     * @return the service target
     * @deprecated Use {@link #getRequirementServiceTarget()} instead.
     */
    @Deprecated(forRemoval = true)
    default ServiceTarget getServiceTarget() {
        return this.getRequirementServiceTarget();
    }

    /**
     * Returns the target into which this phase should install services.
     * <b>Please note</b> that services added via this context do <b>not</b> have any implicit dependencies by default;
     * any root-level deployment services that you add should depend on the service name of the current phase,
     * acquired via the {@link #getPhaseServiceName()} method.
     *
     * @return the service target
     */
    RequirementServiceTarget getRequirementServiceTarget();

    /**
     * Get the service registry for the container, which may be used to look up services.
     *
     * @return the service registry
     */
    ServiceRegistry getServiceRegistry();

    /**
     * Get the persistent deployment unit context for this deployment unit.
     *
     * @return the deployment unit context
     */
    DeploymentUnit getDeploymentUnit();

    /**
     * Get the phase that this processor applies to.
     *
     * @return the phase
     */
    Phase getPhase();

    /**
     * Adds a dependency on the service to the next phase service. The service value will be make available as an attachment
     * under the {@link DeploymentPhaseContext} for the phase.
     * <p/>
     * If the attachment represents an {@link AttachmentList} type then the value is added to the attachment list.
     *
     * @param <T> the type of the injected value
     * @param serviceName The service name to add to {@link Attachments#NEXT_PHASE_DEPS}
     * @param attachmentKey The AttachmentKey to attach the service result under.
     * @throws IllegalStateException If this is the last phase
     */
    <T> void addDependency(ServiceName serviceName, AttachmentKey<T> attachmentKey);

    /**
     * Adds a dependency on the service to the next phase service.
     *
     * @param <T> the type of the injected value
     * @param serviceName the service name to add to {@link Attachments#NEXT_PHASE_DEPS}
     * @param supplier the supplier into which the dependency value is injected
     * @throws IllegalStateException If this is the last phase
     * @deprecated Use {@link #requires(ServiceDependency)} instead.
     */
    @Deprecated(forRemoval = true)
    default <T> void requires(ServiceName serviceName, DelegatingSupplier<T> supplier) {
        ServiceDependency<T> dependency = ServiceDependency.on(serviceName);
        supplier.set(dependency);
        this.requires(dependency);
    }

    /**
     * Adds the specified dependency to the next phase service.
     *
     * @param <T> the dependency type
     * @param dependency a service dependency
     * @throws IllegalStateException If this is the last phase
     */
    <T> void requires(ServiceDependency<T> dependency);

    /**
     * Adds a dependency on the service to the next phase service. The service value will be make available as an attachment to
     * the {@link DeploymentUnit}. This attachment will be removed when the phase service for the next phase stops.
     * <p/>
     * If the attachment represents an {@link AttachmentList} type then the value is added to the attachment list.
     *
     * @param <T> The type of the injected value
     * @param serviceName The service name to add to {@link Attachments#NEXT_PHASE_DEPS}
     * @param attachmentKey The AttachmentKey to attach the service result under.
     * @throws IllegalStateException If this is the last phase
     */
    <T> void addDeploymentDependency(ServiceName serviceName, AttachmentKey<T> attachmentKey);

}
