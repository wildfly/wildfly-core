/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.tool;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.ContentConflictsException;
import org.jboss.as.patching.PatchInfo;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.VerbosePatchInfo;
import org.jboss.as.patching.installation.PatchableTarget.TargetInfo;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentType;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchElement;
import org.jboss.as.patching.tool.PatchingHistory.Entry;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class PatchOperationTarget {

    static final PathElement CORE_SERVICES = PathElement.pathElement(CORE_SERVICE, "patching");

    /**
     * Create a local target.
     *
     * @param jbossHome    the jboss home
     * @param moduleRoots  the module roots
     * @param bundlesRoots the bundle roots
     * @return             the local target
     * @throws IOException
     */
    public static final PatchOperationTarget createLocal(final File jbossHome, List<File> moduleRoots, List<File> bundlesRoots) throws IOException {
        final PatchTool tool = PatchTool.Factory.createLocalTool(jbossHome, moduleRoots, bundlesRoots);
        return new LocalPatchOperationTarget(tool);
    }

    /**
     * Create a standalone target.
     *
     * @param controllerClient the connected controller client to a standalone instance.
     * @return the remote target
     */
    public static final PatchOperationTarget createStandalone(final ModelControllerClient controllerClient) {
        final PathAddress address = PathAddress.EMPTY_ADDRESS.append(CORE_SERVICES);
        return new RemotePatchOperationTarget(address, controllerClient);
    }

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

    protected abstract ModelNode history() throws PatchingException;
    protected abstract ModelNode history(String streamName) throws PatchingException;

    protected abstract ModelNode applyPatch(final File file, final ContentPolicyBuilderImpl builder) throws PatchingException;

    protected abstract ModelNode rollback(final String patchId, final ContentPolicyBuilderImpl builder, boolean rollbackTo, final boolean restoreConfiguration) throws PatchingException;
    protected abstract ModelNode rollback(final String streamName, final String patchId,
            final ContentPolicyBuilderImpl builder, boolean rollbackTo, final boolean restoreConfiguration) throws PatchingException;

    protected abstract ModelNode rollbackLast(final ContentPolicyBuilderImpl builder, final boolean restoreConfiguration) throws PatchingException;
    protected abstract ModelNode rollbackLast(final String streamName, final ContentPolicyBuilderImpl builder, final boolean restoreConfiguration) throws PatchingException;

    protected static class LocalPatchOperationTarget extends PatchOperationTarget {

        private final PatchTool tool;
        public LocalPatchOperationTarget(PatchTool tool) {
            this.tool = tool;
        }

        @Override
        protected ModelNode streams() throws PatchingException {
            final List<String> streams = tool.getPatchStreams();
            final ModelNode result = new ModelNode();
            result.get(OUTCOME).set(SUCCESS);
            final ModelNode list = result.get(RESULT).setEmptyList();
            for(final String stream : streams) {
                list.add(stream);
            }
            return result;
        }

        @Override
        protected ModelNode info() throws PatchingException {
            return info(null);
        }

        @Override
        protected ModelNode info(String streamName) throws PatchingException {
            final PatchInfo info = tool.getPatchInfo(streamName);
            final ModelNode response = new ModelNode();
            response.get(OUTCOME).set(SUCCESS);
            final ModelNode result = response.get(RESULT);
            result.get(Constants.VERSION).set(info.getVersion());
            result.get(Constants.CUMULATIVE).set(info.getCumulativePatchID());
            result.get(Constants.PATCHES).setEmptyList();
            for(final String patch : info.getPatchIDs()) {
                result.get(Constants.PATCHES).add(patch);
            }
            if(info instanceof VerbosePatchInfo) {
                final VerbosePatchInfo vInfo = (VerbosePatchInfo) info;
                if(vInfo.hasLayers()) {
                    final ModelNode layersNode = result.get(Constants.LAYER);
                    for(String name : vInfo.getLayerNames()) {
                        final TargetInfo layerInfo = vInfo.getLayerInfo(name);
                        final ModelNode layerNode = layersNode.get(name);
                        layerNode.get(Constants.CUMULATIVE).set(layerInfo.getCumulativePatchID());
                        final ModelNode patchesNode = layerNode.get(Constants.PATCHES).setEmptyList();
                        if(!layerInfo.getPatchIDs().isEmpty()) {
                            for(String patchId : layerInfo.getPatchIDs()) {
                                patchesNode.add(patchId);
                            }
                        }
                    }
                }
                if(vInfo.hasAddOns()) {
                    final ModelNode layerNode = result.get(Constants.ADD_ON);
                    for(String name : vInfo.getAddOnNames()) {
                        final TargetInfo layerInfo = vInfo.getAddOnInfo(name);
                        layerNode.get(name, Constants.CUMULATIVE).set(layerInfo.getCumulativePatchID());
                        final ModelNode patchesNode = layerNode.get(Constants.PATCHES).setEmptyList();
                        if(!layerInfo.getPatchIDs().isEmpty()) {
                            for(String patchId : layerInfo.getPatchIDs()) {
                                patchesNode.add(patchId);
                            }
                        }
                    }
                }
            }
            return response;
        }

        @Override
        protected ModelNode history() {
            return history(null);
        }

        @Override
        protected ModelNode history(String streamName) {
            final ModelNode result = new ModelNode();
            result.get(OUTCOME).set(SUCCESS);
            try {
                result.get(RESULT).set(tool.getPatchingHistory(streamName).getHistory());
            } catch (PatchingException e) {
                return formatFailedResponse(e);
            }
            return result;
        }

        @Override
        protected ModelNode applyPatch(final File file, final ContentPolicyBuilderImpl builder) {
            final ContentVerificationPolicy policy = builder.createPolicy();
            ModelNode result = new ModelNode();
            try {
                PatchingResult apply = tool.applyPatch(file, policy);
                apply.commit();
                result.get(OUTCOME).set(SUCCESS);
                result.get(RESULT).setEmptyObject();
            } catch (PatchingException e) {
                return formatFailedResponse(e);
            }
            return result;
        }

        @Override
        protected ModelNode rollback(final String patchId, final ContentPolicyBuilderImpl builder, boolean rollbackTo, boolean resetConfiguration) {
            return rollback(null, patchId, builder, rollbackTo, resetConfiguration);
        }

        @Override
        protected ModelNode rollback(final String streamName, final String patchId, final ContentPolicyBuilderImpl builder, boolean rollbackTo, boolean resetConfiguration) {
            final ContentVerificationPolicy policy = builder.createPolicy();
            ModelNode result = new ModelNode();
            try {
                PatchingResult rollback = tool.rollback(streamName, patchId, policy, rollbackTo, resetConfiguration);
                rollback.commit();
                result.get(OUTCOME).set(SUCCESS);
                result.get(RESULT).setEmptyObject();
            } catch (PatchingException e) {
                return formatFailedResponse(e);
            }
            return result;
        }

        @Override
        protected ModelNode rollbackLast(final ContentPolicyBuilderImpl builder, boolean restoreConfiguration) {
            return rollbackLast(null, builder, restoreConfiguration);
        }

        @Override
        protected ModelNode rollbackLast(final String streamName, final ContentPolicyBuilderImpl builder, boolean restoreConfiguration) {
            final ContentVerificationPolicy policy = builder.createPolicy();
            ModelNode result = new ModelNode();
            try {
                PatchingResult rollback = tool.rollbackLast(streamName, policy, restoreConfiguration);
                rollback.commit();
                result.get(OUTCOME).set(SUCCESS);
                result.get(RESULT).setEmptyObject();
            } catch (PatchingException e) {
                return formatFailedResponse(e);
            }
            return result;
        }

        @Override
        protected ModelNode info(String patchId, boolean verbose) throws PatchingException {
            return info(null, patchId, verbose);
        }

        @Override
        protected ModelNode info(String streamName, String patchId, boolean verbose) throws PatchingException {
            if(patchId == null) {
                throw new IllegalArgumentException("patchId is null");
            }
            final PatchingHistory history = tool.getPatchingHistory(streamName);
            try {
                final PatchingHistory.Iterator iterator = history.iterator();
                while(iterator.hasNext()) {
                    final Entry next = iterator.next();
                    if(patchId.equals(next.getPatchId())) {
                        final ModelNode response = new ModelNode();
                        response.get(OUTCOME).set(SUCCESS);
                        final ModelNode result = response.get(RESULT);
                        result.get(Constants.PATCH_ID).set(next.getPatchId());
                        result.get(Constants.TYPE).set(next.getType().getName());
                        final Patch metadata = next.getMetadata();
                        result.get(Constants.IDENTITY_NAME).set(metadata.getIdentity().getName());
                        result.get(Constants.IDENTITY_VERSION).set(metadata.getIdentity().getVersion());
                        result.get(Constants.DESCRIPTION).set(next.getMetadata().getDescription());
                        if (next.getMetadata().getLink() != null) {
                            result.get(Constants.LINK).set(next.getMetadata().getLink());
                        }

                        if (verbose) {
                            final ModelNode elements = result.get(Constants.ELEMENTS).setEmptyList();
                            for(PatchElement e : metadata.getElements()) {
                                final ModelNode element = new ModelNode();
                                element.get(Constants.PATCH_ID).set(e.getId());
                                element.get(Constants.NAME).set(e.getProvider().getName());
                                element.get(Constants.TYPE).set(e.getProvider().isAddOn() ? Constants.ADD_ON : Constants.LAYER);
                                element.get(Constants.DESCRIPTION).set(e.getDescription());
                                elements.add(element);
                            }
                        }
                        return response;
                    }
                }
            } catch (PatchingException e) {
                return formatFailedResponse(e);
            }
            return formatFailedResponse(PatchLogger.ROOT_LOGGER.patchNotFoundInHistory(patchId).getLocalizedMessage());
        }
    }

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
        protected ModelNode history() throws PatchingException {
            return history(null);
        }

        @Override
        protected ModelNode history(String streamName) throws PatchingException {
            final ModelNode operation = new ModelNode();
            operation.get(OP).set(Constants.SHOW_HISTORY);
            operation.get(OP_ADDR).set(address.toModelNode());
            if(streamName != null) {
                operation.get(ModelDescriptionConstants.OP_ADDR).add(Constants.PATCH_STREAM, streamName);
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

    static ModelNode formatFailedResponse(final String msg) {
        final ModelNode result = new ModelNode();
        result.get(OUTCOME).set(FAILED);
        result.get(FAILURE_DESCRIPTION, Constants.MESSAGE).set(msg);
        return result;
    }

    static ModelNode formatFailedResponse(final PatchingException e) {
        final ModelNode result = new ModelNode();
        result.get(OUTCOME).set(FAILED);
        formatFailedResponse(e, result.get(FAILURE_DESCRIPTION));
        return result;
    }

    public static void formatFailedResponse(final PatchingException e, final ModelNode failureDescription) {
        if(e instanceof ContentConflictsException) {
            failureDescription.get(Constants.MESSAGE).set(PatchLogger.ROOT_LOGGER.detectedConflicts());
            final ModelNode conflicts = failureDescription.get(Constants.CONFLICTS);
            for(final ContentItem item : ((ContentConflictsException)e).getConflicts()) {
                final ContentType type = item.getContentType();
                switch (type) {
                    case BUNDLE:
                        conflicts.get(Constants.BUNDLES).add(item.getRelativePath());
                        break;
                    case MODULE:
                        conflicts.get(Constants.MODULES).add(item.getRelativePath());
                        break;
                    case MISC:
                        conflicts.get(Constants.MISC).add(item.getRelativePath());
                        break;
                }
            }
        } else {
            failureDescription.set(e.getLocalizedMessage());
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
