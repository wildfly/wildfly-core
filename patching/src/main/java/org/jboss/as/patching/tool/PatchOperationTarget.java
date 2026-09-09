/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tool;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.io.File;
import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.PatchingException;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class PatchOperationTarget {

    static final PathElement CORE_SERVICES = PathElement.pathElement(CORE_SERVICE, "patching");

    /**
     * Create a host target.
     *
     * @param hostName the host name
     * @param client the connected controller client to the master host.
     * @return the remote target
     */
    public static final PatchOperationTarget createHost(final String hostName, final ModelControllerClient client) {
        final PathElement host = PathElement.pathElement(HOST, hostName);
        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(host, CORE_SERVICES);
        return new RemotePatchOperationTarget(address, client);
    }

    //

    protected abstract ModelNode streams() throws PatchingException;

    protected abstract ModelNode info() throws PatchingException;
    protected abstract ModelNode info(String streamName) throws PatchingException;

    protected abstract ModelNode info(String patchId, boolean verbose) throws PatchingException;
    protected abstract ModelNode info(String streamName, String patchId, boolean verbose) throws PatchingException;

    protected ModelNode history() throws PatchingException {
        return history(false);
    }
    protected abstract ModelNode history(boolean excludeAgedOut) throws PatchingException;
    protected ModelNode history(String streamName) throws PatchingException {
        return history(streamName, false);
    }
    protected abstract ModelNode history(String streamName, boolean excludeAgedOut) throws PatchingException;

    protected abstract ModelNode applyPatch(final File file, final ContentPolicyBuilderImpl builder) throws PatchingException;

    protected abstract ModelNode rollback(final String patchId, final ContentPolicyBuilderImpl builder, boolean rollbackTo, final boolean restoreConfiguration) throws PatchingException;
    protected abstract ModelNode rollback(final String streamName, final String patchId,
            final ContentPolicyBuilderImpl builder, boolean rollbackTo, final boolean restoreConfiguration) throws PatchingException;

    protected abstract ModelNode rollbackLast(final ContentPolicyBuilderImpl builder, final boolean restoreConfiguration) throws PatchingException;
    protected abstract ModelNode rollbackLast(final String streamName, final ContentPolicyBuilderImpl builder, final boolean restoreConfiguration) throws PatchingException;

    protected static class RemotePatchOperationTarget extends PatchOperationTarget {

        private final PathAddress address;
        private final ModelControllerClient client;

        public RemotePatchOperationTarget(PathAddress address, ModelControllerClient client) {
            this.address = address;
            this.client = client;
        }

        @Override
        protected ModelNode streams() throws PatchingException {
            final ModelNode operation = new ModelNode();
            operation.get(ModelDescriptionConstants.OP_ADDR).set(address.toModelNode());
            operation.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION);
            operation.get(ModelDescriptionConstants.CHILD_TYPE).set(Constants.PATCH_STREAM);
            return executeOp(operation);
        }

        @Override
        protected ModelNode info() throws PatchingException {
            return info(null);
        }

        @Override
        protected ModelNode info(String streamName) throws PatchingException {
            final ModelNode operation = new ModelNode();
            operation.get(ModelDescriptionConstants.OP).set(Constants.PATCH_INFO);
            operation.get(ModelDescriptionConstants.OP_ADDR).set(address.toModelNode());
            if(streamName != null) {
                operation.get(ModelDescriptionConstants.OP_ADDR).add(Constants.PATCH_STREAM, streamName);
            }
            operation.get(Constants.VERBOSE).set(true);
            return executeOp(operation);
        }

        @Override
        protected ModelNode history(boolean excludeAgedOut) throws PatchingException {
            return history(null, excludeAgedOut);
        }

        @Override
        protected ModelNode history(String streamName, boolean excludeAgedOut) throws PatchingException {
            final ModelNode operation = new ModelNode();
            operation.get(OP).set(Constants.SHOW_HISTORY);
            operation.get(OP_ADDR).set(address.toModelNode());
            if(streamName != null) {
                operation.get(ModelDescriptionConstants.OP_ADDR).add(Constants.PATCH_STREAM, streamName);
            }
            if(excludeAgedOut) {
                operation.get(Constants.EXCLUDE_AGED_OUT).set(true);
            }
            return executeOp(operation);
        }

        @Override
        protected ModelNode applyPatch(final File file, final ContentPolicyBuilderImpl policyBuilder) throws PatchingException {
            final ModelNode operation = createOperation(Constants.PATCH, address.toModelNode(), policyBuilder);
            operation.get(INPUT_STREAM_INDEX).set(0);
            final OperationBuilder operationBuilder = OperationBuilder.create(operation);
            operationBuilder.addFileAsAttachment(file);
            return executeOp(operationBuilder.build());
        }

        @Override
        protected ModelNode rollback(String patchId, ContentPolicyBuilderImpl builder, boolean rollbackTo, boolean resetConfiguration) throws PatchingException {
            return rollback(null, patchId, builder, rollbackTo, resetConfiguration);
        }

        @Override
        protected ModelNode rollback(String streamName, String patchId, ContentPolicyBuilderImpl builder, boolean rollbackTo, boolean resetConfiguration) throws PatchingException {
            final ModelNode operation = createOperation(Constants.ROLLBACK, address.toModelNode(), builder);
            operation.get(Constants.PATCH_ID).set(patchId);
            operation.get(Constants.RESET_CONFIGURATION).set(resetConfiguration);
            operation.get(Constants.ROLLBACK_TO).set(rollbackTo);
            if(streamName != null) {
                operation.get(ModelDescriptionConstants.OP_ADDR).add(Constants.PATCH_STREAM, streamName);
            }
            return executeOp(operation);
        }

        @Override
        protected ModelNode rollbackLast(ContentPolicyBuilderImpl builder, boolean restoreConfiguration) throws PatchingException {
            return rollbackLast(null, builder, restoreConfiguration);
        }

        @Override
        protected ModelNode rollbackLast(String streamName, ContentPolicyBuilderImpl builder, boolean restoreConfiguration) throws PatchingException {
            final ModelNode operation = createOperation(Constants.ROLLBACK_LAST, address.toModelNode(), builder);
            operation.get(Constants.RESET_CONFIGURATION).set(restoreConfiguration);
            if(streamName != null) {
                operation.get(ModelDescriptionConstants.OP_ADDR).add(Constants.PATCH_STREAM, streamName);
            }
            return executeOp(operation);
        }

        @Override
        protected ModelNode info(String patchId, boolean verbose) throws PatchingException {
            return info(null, patchId, verbose);
        }

        @Override
        protected ModelNode info(String streamName, String patchId, boolean verbose) throws PatchingException {
            final ModelNode operation = new ModelNode();
            operation.get(ModelDescriptionConstants.OP).set(Constants.PATCH_INFO);
            operation.get(ModelDescriptionConstants.OP_ADDR).set(address.toModelNode());
            operation.get(Constants.PATCH_ID).set(patchId);
            if(streamName != null) {
                operation.get(ModelDescriptionConstants.OP_ADDR).add(Constants.PATCH_STREAM, streamName);
            }
            if(verbose) {
                operation.get(Constants.VERBOSE).set(true);
            }
            return executeOp(operation);
        }

        protected ModelNode executeOp(ModelNode operation) throws PatchingException {
            try {
                return client.execute(operation);
            } catch (IOException e) {
                throw new PatchingException("Failed to execute operation " + operation, e);
            }
        }

        protected ModelNode executeOp(Operation operation) throws PatchingException {
            try {
                return client.execute(operation);
            } catch (IOException e) {
                throw new PatchingException("Failed to execute operation " + operation.getOperation(), e);
            }
        }
    }

    static ModelNode createOperation(final String operationName, final ModelNode addr, final ContentPolicyBuilderImpl builder) {
        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set(operationName);
        operation.get(ModelDescriptionConstants.OP_ADDR).set(addr);

        // Process the policy
        operation.get(Constants.OVERRIDE_MODULES).set(builder.ignoreModulesChanges);
        operation.get(Constants.OVERRIDE_ALL).set(builder.overrideAll);
        if(! builder.override.isEmpty()) {
            for(final String o : builder.override) {
                operation.get(Constants.OVERRIDE).add(o);
            }
        }
        if(! builder.preserve.isEmpty()) {
            for(final String p : builder.preserve) {
                operation.get(Constants.PRESERVE).add(p);
            }
        }
        return operation;
    }
}
