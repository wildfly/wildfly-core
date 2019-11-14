/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.server.deployment.module;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;

/**
 * Coordinates handling of {@link org.jboss.as.server.deployment.Attachments#ADDITIONAL_MODULES} updates
 * among concurrent threads handling the top level deployment and possible subdeployments.
 */
public final class AdditionalModuleCoordinator {

    private final CountDownLatch rootStructureLatch = new CountDownLatch(1);
    private final CountDownLatch classPathLatch = new CountDownLatch(1);
    private final List<AdditionalModuleSpecification> additionalModules = new CopyOnWriteArrayList<>();
    private final DeploymentUnit rootDeploymentUnit;
    // @GuardedBy this
    private AtomicInteger deploymentServiceCount = new AtomicInteger(1); // start with one for the top level deployment

    /**
     * Creates a new AdditionalModuleCoordinator for a top level deployment.
     * @param rootDeploymentUnit {@link DeploymentUnit} for the top level deployment. Cannot be {@code null}.
     */
    public AdditionalModuleCoordinator(DeploymentUnit rootDeploymentUnit) {
        this.rootDeploymentUnit = rootDeploymentUnit;
    }

    /**
     * Record that a SubDeploymentUnitService is being installed and thus must call {@link #registerClassPathAdditionalModules(List)}
     * before {@link #awaitManifestClassPathAdditionalModules()} can unblock. SubDeploymentProcessor will invoke this
     * for any deployment before ManifestClassPathProcessor invokes registerClassPathAdditionalModules for that deployment,
     * thus ensuring that the count of expected registerClassPathAdditionalModules calls is not subject to races.
     */
    public void recordSubdeployment() {
        deploymentServiceCount.incrementAndGet();
    }

    /**
     * Registration of additional modules identified for the top-level deployment by DeploymentStructureDescriptorParser.
     *
     * @param additionalModules the additional modules. Cannot be {@code null}, may be empty.
     */
    public void registerRootAdditionalModules(Collection<AdditionalModuleSpecification> additionalModules) {
        this.additionalModules.addAll(additionalModules);
        rootStructureLatch.countDown();
    }

    /**
     * Obtains modules registered via {@link #registerRootAdditionalModules(Collection)}, blocking until they are registered.
     *
     * @return the additional modules. Will not return {@code null}.
     *
     * @throws DeploymentUnitProcessingException if interrupted while blocking.
     */
    public List<AdditionalModuleSpecification> getAdditionalModules() throws DeploymentUnitProcessingException {
        try {
            rootStructureLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentUnitProcessingException(e);
        }
        return additionalModules;
    }

    /**
     * Registration of additional modules identified by ManifestClassPathProcessor. When invoked by all deployment unit
     * services associated with a top level deploymnt, all additional modules that have been registered will be added to
     * the {@link Attachments#ADDITIONAL_MODULES} attachment list. <strong>Must be invoked by the top level deployment
     * and each subdeployment, even if there are no additional modules for that item.</strong>
     *
     * @param newAdditionalModules the additional modules. Cannot be {@code null}, may be empty.
     */
    void registerClassPathAdditionalModules(List<AdditionalModuleSpecification> newAdditionalModules) {
        additionalModules.addAll(newAdditionalModules);

        if (deploymentServiceCount.decrementAndGet() == 0) {
            // Parent and all child deployments have completed additional module registration;
            // record the result with the root deployment unit for use by subsequent DUPs.
            for (AdditionalModuleSpecification spec : additionalModules) {
                rootDeploymentUnit.addToAttachmentList(Attachments.ADDITIONAL_MODULES, spec);
            }
            classPathLatch.countDown();
        }
    }

    /**
     * Blocks until the top level deployment and all subdeployments have called
     * {@link #registerClassPathAdditionalModules(List)}.
     *
     * @throws DeploymentUnitProcessingException if interrupted while blocking.
     */
    void awaitManifestClassPathAdditionalModules() throws DeploymentUnitProcessingException {
        try {
            classPathLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentUnitProcessingException(e);
        }
    }
}
