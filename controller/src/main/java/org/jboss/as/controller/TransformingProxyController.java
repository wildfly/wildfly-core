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

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;

import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.remote.RemoteProxyController;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.remote.TransactionalProtocolHandlers;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.dmr.ModelNode;

/**
 * A {@link ProxyController} with transformation capabilities.
 *
 * @author Emanuel Muckenhuber
 */
public interface TransformingProxyController extends ProxyController {

    /**
     * Get the underlying protocol client.
     *
     * @return the protocol client
     */
    TransactionalProtocolClient getProtocolClient();

    /**
     * Get the Transformers!
     *
     * @return the transformers
     */
    Transformers getTransformers();

    /**
     * Transform the operation.
     *
     * @param context the operation context
     * @param operation the operation to transform.
     * @return the transformed operation
     * @throws OperationFailedException
     */
    OperationTransformer.TransformedOperation transformOperation(OperationContext context, ModelNode operation) throws OperationFailedException;

    /**
     * Transform the operation.
     *
     * @param parameters parameters that drive the transformation
     * @param operation the operation to transform.
     * @return the transformed operation
     * @throws OperationFailedException
     */
    OperationTransformer.TransformedOperation transformOperation(Transformers.TransformationInputs parameters, ModelNode operation) throws OperationFailedException;


    /**
     * Factory methods for creating a {@link TransformingProxyController}
     */
    class Factory {

        /**
         * Creates a {@link TransactionalProtocolClient} based on the given {@code channelAssociation} and then
         * uses that to create a {@link TransformingProxyController}.
         *
         * @param channelAssociation the channel handler. Cannot be {@code null}
         * @param transformers transformers to use for transforming resources and operations. Cannot be {@code null}
         * @param pathAddress address under which the proxy controller is registered in the resource tree
         * @param addressTranslator translator to use for converting local addresses to addresses appropriate for the target process
         * @return the proxy controller. Will not be {@code null}
         */
        public static TransformingProxyController create(final ManagementChannelHandler channelAssociation, final Transformers transformers, final PathAddress pathAddress, final ProxyOperationAddressTranslator addressTranslator) {
            final TransactionalProtocolClient client = TransactionalProtocolHandlers.createClient(channelAssociation);
            return create(client, transformers, pathAddress, addressTranslator);
        }

        /**
         * Creates a {@link TransformingProxyController} based on the given {@link TransactionalProtocolClient}.
         * @param client the client for communicating with the target process. Cannot be {@code null}
         * @param transformers transformers to use for transforming resources and operations. Cannot be {@code null}
         * @param pathAddress address under which the proxy controller is registered in the resource tree
         * @param addressTranslator translator to use for converting local addresses to addresses appropriate for the target process
         * @return the proxy controller. Will not be {@code null}
         */
        public static TransformingProxyController create(final TransactionalProtocolClient client, final Transformers transformers, final PathAddress pathAddress, final ProxyOperationAddressTranslator addressTranslator) {
            final ModelVersion targetKernelVersion = transformers.getTarget().getVersion();
            final RemoteProxyController proxy = RemoteProxyController.create(client, pathAddress, addressTranslator, targetKernelVersion);
            final Transformers delegating = new Transformers() {
                @Override
                public TransformationTarget getTarget() {
                    return transformers.getTarget();
                }

                @Override
                public OperationTransformer.TransformedOperation transformOperation(final TransformationContext context, final ModelNode original) throws OperationFailedException {
                    final ModelNode operation = proxy.translateOperationForProxy(original);
                    return transformers.transformOperation(context, operation);
                }

                @Override
                public Resource transformResource(ResourceTransformationContext context, Resource resource) throws OperationFailedException {
                    return transformers.transformResource(context, resource);
                }

                @Override
                public OperationTransformer.TransformedOperation transformOperation(TransformationInputs transformationParameters, ModelNode original) throws OperationFailedException {
                    final ModelNode operation = proxy.translateOperationForProxy(original);
                    return transformers.transformOperation(transformationParameters, operation);
                }

                @Override
                public Resource transformRootResource(TransformationInputs transformationParameters, Resource resource) throws OperationFailedException {
                    return transformers.transformRootResource(transformationParameters, resource);
                }

                @Override
                public Resource transformRootResource(TransformationInputs transformationParameters, Resource resource, ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) throws OperationFailedException {
                    return transformers.transformRootResource(transformationParameters, resource, ignoredTransformationRegistry);
                }
            };
            return create(proxy, delegating);
        }

        private static TransformingProxyController create(final RemoteProxyController delegate, Transformers transformers) {
            return new TransformingProxyControllerImpl(transformers, delegate);
        }

        private static class TransformingProxyControllerImpl implements TransformingProxyController {

            private final RemoteProxyController proxy;
            private final Transformers transformers;

            public TransformingProxyControllerImpl(Transformers transformers, RemoteProxyController proxy) {
                this.transformers = transformers;
                this.proxy = proxy;
            }

            @Override
            public TransactionalProtocolClient getProtocolClient() {
                return proxy.getTransactionalProtocolClient();
            }

            @Override
            public Transformers getTransformers() {
                return transformers;
            }

            @Override
            public PathAddress getProxyNodeAddress() {
                return proxy.getProxyNodeAddress();
            }

            @Override
            public OperationTransformer.TransformedOperation transformOperation(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                return transformOperation(Transformers.TransformationInputs.getOrCreate(context), operation);
            }

            @Override
            public OperationTransformer.TransformedOperation transformOperation(Transformers.TransformationInputs parameters, ModelNode operation) throws OperationFailedException {
                //Some transformers don't propagate the headers, back them up here and add them again
                ModelNode operationHeaders = operation.hasDefined(OPERATION_HEADERS) ? operation.get(OPERATION_HEADERS) : null;
                OperationTransformer.TransformedOperation transformed = transformers.transformOperation(parameters, operation);

                if (operationHeaders != null) {
                    ModelNode transformedOp = transformed.getTransformedOperation();
                    if (transformedOp != null && !transformedOp.hasDefined(OPERATION_HEADERS)) {
                        transformedOp.get(OPERATION_HEADERS).set(operationHeaders);
                    }
                }
                return transformed;
            }

            @Override
            public void execute(final ModelNode operation, final OperationMessageHandler handler, final ProxyOperationControl control,
                                final OperationAttachments attachments, final BlockingTimeout blockingTimeout) {
                // Execute untransformed
                proxy.execute(operation, handler, control, attachments, blockingTimeout);
            }

            @Override
            public ModelVersion getKernelModelVersion() {
                return proxy.getKernelModelVersion();
            }
        }

    }

}
