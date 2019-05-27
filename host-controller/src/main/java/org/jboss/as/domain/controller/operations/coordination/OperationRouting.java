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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.operations.global.GlobalOperationHandlers.STD_READ_OPS;
import static org.jboss.as.controller.operations.global.GlobalOperationHandlers.STD_WRITE_OPS;

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
import org.jboss.as.host.controller.logging.HostControllerLogger;
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
        return determineRouting(operation, localHostControllerInfo, rootRegistration, hostNames, false);
    }

    private static OperationRouting determineRouting(final ModelNode operation, final LocalHostControllerInfo localHostControllerInfo,
                                                     final ImmutableManagementResourceRegistration rootRegistration,
                                                     final Set<String> hostNames, final boolean compositeStep) throws OperationFailedException {
        HostControllerLogger.ROOT_LOGGER.tracef("Determining routing for %s", operation);
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String operationName = operation.require(OP).asString();
        final Set<OperationEntry.Flag> operationFlags = resolveOperationFlags(address, operationName, rootRegistration,
                compositeStep, localHostControllerInfo.getLocalHostName());
        return determineRouting(operation, address, operationName, operationFlags, localHostControllerInfo, rootRegistration, hostNames);
    }

    private static Set<OperationEntry.Flag> resolveOperationFlags(final PathAddress address, final String operationName,
                                                                  final ImmutableManagementResourceRegistration rootRegistration,
                                                                  final boolean compositeStep,
                                                                  final String localHostName) throws OperationFailedException {
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
        } else if (compositeStep && isDomainOrLocalHost(address, localHostName)) {
            // WFCORE-323. This could be a subsystem step in a composite where an earlier step adds
            // the extension. So the registration of the subsystem would not be done yet.
            // See if we can figure out flags usable for routing.
            PathAddress subsystemRoot = findSubsystemRootAddress(address);
            if (subsystemRoot != null // else this isn't for a subsystem
                    // Only bother if the subsystem root is not registered.
                    // If the root is registered any child is already registered too.
                    && (address.equals(subsystemRoot) || rootRegistration.getSubModel(subsystemRoot) == null)) {

                if (STD_READ_OPS.contains(operationName)) {
                    // One of the global read ops. OperationEntry.Flag.DOMAIN_PUSH_TO_SERVERS handling
                    // (which is only meant for core ops) is not supported.
                    result = Collections.singleton(OperationEntry.Flag.READ_ONLY);
                } else if (STD_WRITE_OPS.contains(operationName)) {
                    // One of the global write ops, or 'add' or 'remove'.
                    // Not read only and OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY handling
                    // (which is only meant for core ops) is not supported.
                    result = Collections.emptySet();
                } // else we don't know what this op does so we can't provide a routing.
            }
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

    private static boolean isDomainOrLocalHost(PathAddress address, String localHostName) {
        return address.size() == 0 || !address.getElement(0).getKey().equals(HOST) || address.getElement(0).getValue().equals(localHostName);
    }


    private static PathAddress findSubsystemRootAddress(PathAddress address) {
        PathAddress result = null;
        int size = address.size();
        if (size > 1) {
            int subsystemKey = Integer.MAX_VALUE;
            String firstKey = address.getElement(0).getKey();
            if (HOST.equals(firstKey) || PROFILE.equals(firstKey)) {
                subsystemKey = 1;
            }
            if (size > subsystemKey
                    && SUBSYSTEM.equals(address.getElement(subsystemKey).getKey())) {
                result = subsystemKey == size - 1 ? address : address.subAddress(0, subsystemKey + 1);
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
        boolean profileChildOp = false;
        if (address.size() > 0) {
            PathElement first = address.getElement(0);
            if (HOST.equals(first.getKey())) {
                if (first.isMultiTarget()) {
                    if (first.isWildcard()) {
                        targetHost = new HashSet<>(hostNames);
                        targetHost.add(localHostControllerInfo.getLocalHostName());
                    } else {
                        targetHost = new HashSet<>();
                        Collections.addAll(targetHost, first.getSegments());
                    }
                } else {
                    targetHost = Collections.singleton(first.getValue());
                }
            } else if (PROFILE.equals(first.getKey())) {
                profileChildOp = address.size() > 1;
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
                    // A direct request to a server is not handled via two-phase handling
                    // even if the request is for a write op. A write-op to a server
                    // is illegal anyway, so there is no reason to handle it two-phase
                    routing =  new OperationRouting(targetHost, false);
                } else if (!ServerOperationResolver.isHostChildAddressMultiphase(address)) {
                    // Address does not result in changes to child processes
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
                    OperationRouting stepRouting = determineRouting(step, localHostControllerInfo, rootRegistration, hostNames, true);
                    if (stepRouting.isMultiphase()) {
                        twoStep = true;
                        // Make sure we don't loose the information that we have to execute the operation on all hosts
                        fwdToAllHosts = fwdToAllHosts || stepRouting.getHosts().isEmpty();
                    }
//                    if (!localHostControllerInfo.isMasterDomainController()) {
//                        fwdToAllHosts = fwdToAllHosts || stepRouting.getHosts().isEmpty();
//                    }
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
            // Reads execute locally, except those specifically configured for multiphase (DOMAIN_PUSH_TO_SERVERS)
            // or runtime-only ops against profile subsystems (where locally there should be no runtime to read).
            // The HOST_CONTROLLER_ONLY flag forces the read to stay local, primarily as a way to retain legacy
            // behavior for runtime-only ops that were registered against profile subsystems and should not have been
            if (operationFlags.contains(OperationEntry.Flag.READ_ONLY)
                    && ((!operationFlags.contains(OperationEntry.Flag.DOMAIN_PUSH_TO_SERVERS)
                        && (!profileChildOp || !operationFlags.contains(OperationEntry.Flag.RUNTIME_ONLY)))
                    || operationFlags.contains(OperationEntry.Flag.HOST_CONTROLLER_ONLY))) {
                // Execute locally
                routing = new OperationRouting(localHostControllerInfo);
            } else if (!localHostControllerInfo.isMasterDomainController()) {
                // Route to master
                routing = new OperationRouting();
            } else if (operationFlags.contains(OperationEntry.Flag.MASTER_HOST_CONTROLLER_ONLY)) {
                // Deployment ops should be executed on the master DC only
                routing = new OperationRouting(localHostControllerInfo);
            } // else fall through to multiphase routing
        }

        if (routing == null) {
            // Write operation to the model or a read that needs to be pushed to servers; everyone gets it
            routing = new OperationRouting(true);
        }
        DomainControllerLogger.HOST_CONTROLLER_LOGGER.tracef("Routing for operation %s is %s", operation, routing);
        return routing;

    }

    private final Set<String> hosts = new HashSet<String>();
    private final boolean multiphase;

    /** Constructor for domain-level requests where we are not master */
    private OperationRouting() {
        multiphase = false;
    }

    /** Constructor for multi-process ops */
    private OperationRouting(final boolean multiphase) {
        this.multiphase = multiphase;
    }

    /**
     * Constructor for a non-multiphase request routed to this host
     *
     * @param localHostControllerInfo information describing this host
     */
    private OperationRouting(LocalHostControllerInfo localHostControllerInfo) {
        this.hosts.add(localHostControllerInfo.getLocalHostName());
        this.multiphase = false;
    }

    /**
     * Constructor for a request routed to a single host
     *
     * @param hosts the name of the hosts
     * @param multiphase true if a multiphase execution is needed
     */
    private OperationRouting(Set<String> hosts, boolean multiphase) {
        this.hosts.addAll(hosts);
        this.multiphase = multiphase;
    }

    Set<String> getHosts() {
        return hosts;
    }

    String getSingleHost() {
        return hosts.size() == 1 ? hosts.iterator().next() : null;
    }

    boolean isMultiphase() {
        return multiphase;
    }

    boolean isLocalOnly(final String localHostName) {
        return hosts.size() == 1 && hosts.contains(localHostName);
    }

    boolean isLocalCallNeeded(final String localHostName) {
        return hosts.size() == 0 || hosts.contains(localHostName);
    }

    @Override
    public String toString() {
        return "OperationRouting{" +
                "hosts=" + hosts +
                ", multiphase=" + multiphase +
                '}';
    }
}
