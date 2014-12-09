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

package org.jboss.as.domain.controller.operations.coordination;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Encapsulates routing information for an operation executed against a host controller.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class OperationRouting {

    static OperationRouting determineRouting(OperationContext context, ModelNode operation,
                                             final LocalHostControllerInfo localHostControllerInfo, Set<String> hostNames) throws OperationFailedException {
        final ImmutableManagementResourceRegistration rootRegistration = context.getRootResourceRegistration();
        return determineRouting(operation, localHostControllerInfo, rootRegistration, hostNames);
    }

    private static OperationRouting determineRouting(final ModelNode operation, final LocalHostControllerInfo localHostControllerInfo,
                                                     final ImmutableManagementResourceRegistration rootRegistration, Set<String> hostNames) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String operationName = operation.require(OP).asString();
        final Set<OperationEntry.Flag> operationFlags = resolveOperationFlags(address, operationName, rootRegistration);
        return determineRouting(operation, address, operationName, operationFlags, localHostControllerInfo, rootRegistration, hostNames);
    }

    private static Set<OperationEntry.Flag> resolveOperationFlags(final PathAddress address, final String operationName,
                                                         final ImmutableManagementResourceRegistration rootRegistration) throws OperationFailedException {
        Set<OperationEntry.Flag> result = null;
        boolean validAddress = false;

        OperationEntry ope = rootRegistration.getOperationEntry(address, operationName);
        if (ope != null) {
            return ope.getFlags();
        }

        ImmutableManagementResourceRegistration targetReg = rootRegistration.getSubModel(address);
        if (targetReg != null) {
            validAddress = true;
            OperationEntry opE = targetReg.getOperationEntry(PathAddress.EMPTY_ADDRESS, operationName);
            result = opE == null ? null : opE.getFlags();
        }

        if (result == null) {
            // Throw appropriate exception
            if (validAddress) {
                // Bad operation name exception
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(operationName, address));
            } else {
                // Bad address exception
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noSuchResourceType(address));
            }
        }

        return result;
    }

    private static OperationRouting determineRouting(final ModelNode operation,
                                                     final PathAddress address,
                                                     final String operationName,
                                                     final Set<OperationEntry.Flag> operationFlags,
                                                     final LocalHostControllerInfo localHostControllerInfo,
                                                     final ImmutableManagementResourceRegistration rootRegistration,
                                                     Set<String> hostNames)
                                                        throws OperationFailedException {

        OperationRouting routing = null;

        Set<String> targetHost = null;
        boolean compositeOp = false;
        if (address.size() > 0) {
            PathElement first = address.getElement(0);
            if (HOST.equals(first.getKey())) {
                if (first.isMultiTarget()) {
                    if (first.isWildcard()) {
                        targetHost = new HashSet<>();
                        targetHost.addAll(hostNames);
                        targetHost.add(localHostControllerInfo.getLocalHostName());
                    } else {
                        targetHost = new HashSet<>();
                        Collections.addAll(targetHost, first.getSegments());
                    }
                } else {
                    targetHost = Collections.singleton(first.getValue());
                }
            }
        } else {
            compositeOp = COMPOSITE.equals(operationName);
        }

        if (targetHost != null) {
            // Check for read-only flags. But note they will only exist for addresses on this host,
            // as we have no accurate flags for ops registered on remote hosts
            if(operationFlags.contains(OperationEntry.Flag.READ_ONLY) && !operationFlags.contains(OperationEntry.Flag.DOMAIN_PUSH_TO_SERVERS)) {
                routing =  new OperationRouting(targetHost, false);
            }
            // Check if the target is an actual server
            else if(address.size() > 1) {
                PathElement first = address.getElement(1);
                if (SERVER.equals(first.getKey())) {
                    routing =  new OperationRouting(targetHost, false);
                }
            }
            if (routing == null) {
                if(operationFlags.contains(OperationEntry.Flag.HOST_CONTROLLER_ONLY)) {
                    routing = new OperationRouting(targetHost, false);
                } else {
                    // We can't rely on the check for read-only flags above to tell us whether
                    // remote ops are two step. But, we can treat ops solely directed at remote hosts
                    // as non-two step, as we don't need two step execution on this host for such ops
                    boolean twoStep = targetHost.contains(localHostControllerInfo.getLocalHostName());
                    routing = new OperationRouting(targetHost, twoStep);
                }
            }
        } else if (compositeOp) {
            // Recurse into the steps to see what's required
            if (operation.hasDefined(STEPS)) {
                Set<String> allHosts = new HashSet<String>();
                boolean fwdToAllHosts = false;
                boolean twoStep = false;
                for (ModelNode step : operation.get(STEPS).asList()) {
                    OperationRouting stepRouting = determineRouting(step, localHostControllerInfo, rootRegistration, hostNames);
                    if (stepRouting.isTwoStep()) {
                        twoStep = true;
                        // Make sure we don't loose the information that we have to execute the operation on all hosts
                        fwdToAllHosts = fwdToAllHosts || stepRouting.getHosts().isEmpty();
                    }
                    allHosts.addAll(stepRouting.getHosts());
                }
                if (fwdToAllHosts) {
                    routing = new OperationRouting(true);
                } else {
                    routing = new OperationRouting(allHosts, twoStep);
                }
            }
            else {
                // empty; this will be an error but don't deal with it here
                // Let our DomainModel deal with it
                routing = new OperationRouting(localHostControllerInfo);
            }
        } else {
            // Domain level operation
            if (operationFlags.contains(OperationEntry.Flag.READ_ONLY) && !operationFlags.contains(OperationEntry.Flag.DOMAIN_PUSH_TO_SERVERS)) {
                // Direct read of domain model
                routing = new OperationRouting(localHostControllerInfo);
            } else if (!localHostControllerInfo.isMasterDomainController()) {
                // Route to master
                routing = new OperationRouting();
            } else if (operationFlags.contains(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY)) {
                // Deployment ops should be executed on the master DC only
                routing = new OperationRouting(localHostControllerInfo);
            }
        }

        if (routing == null) {
            // Write operation to the model or a read that needs to be pushed to servers; everyone gets it
            routing = new OperationRouting(true);
        }
        DomainControllerLogger.HOST_CONTROLLER_LOGGER.tracef("Routing for operation %s is %s", operation, routing);
        return routing;

    }

    private final Set<String> hosts = new HashSet<String>();
    private final boolean twoStep;

    /** Constructor for domain-level requests where we are not master */
    private OperationRouting() {
        twoStep = false;
    }

    /** Constructor for multi-host ops */
    private OperationRouting(final boolean twoStep) {
        this.twoStep = twoStep;
    }

    /**
     * Constructor for a non-two-step request routed to this host
     *
     * @param localHostControllerInfo information describing this host
     */
    private OperationRouting(LocalHostControllerInfo localHostControllerInfo) {
        this.hosts.add(localHostControllerInfo.getLocalHostName());
        this.twoStep = false;
    }

    /**
     * Constructor for a request routed to a single host
     *
     * @param hosts the name of the hosts
     * @param twoStep true if a two-step execution is needed
     */
    private OperationRouting(Set<String> hosts, boolean twoStep) {
        this.hosts.addAll(hosts);
        this.twoStep = twoStep;
    }

    public Set<String> getHosts() {
        return hosts;
    }

    public String getSingleHost() {
        return hosts.size() == 1 ? hosts.iterator().next() : null;
    }

    public boolean isTwoStep() {
        return twoStep;
    }

    public boolean isLocalOnly(final String localHostName) {
        return hosts.size() == 1 && hosts.contains(localHostName);
    }

    public boolean isLocalCallNeeded(final String localHostName) {
        return hosts.size() == 0 || hosts.contains(localHostName);
    }

    @Override
    public String toString() {
        return "OperationRouting{" +
                "hosts=" + hosts +
                ", twoStep=" + twoStep +
                '}';
    }
}
