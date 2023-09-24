/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers;

import static org.jboss.as.controller.client.helpers.ClientConstants.ADD;
import static org.jboss.as.controller.client.helpers.ClientConstants.COMPOSITE;
import static org.jboss.as.controller.client.helpers.ClientConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP_ADDR;
import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.RECURSIVE;
import static org.jboss.as.controller.client.helpers.ClientConstants.REMOVE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.jboss.as.controller.client.helpers.ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.client.helpers.ClientConstants.STEPS;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;
import static org.jboss.as.controller.client.helpers.ClientConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.VALUE;
import static org.jboss.as.controller.client.helpers.ClientConstants.WRITE_ATTRIBUTE_OPERATION;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;

import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A helper class for various operation tasks. Includes helpers to create standard operations, check whether the
 * operation was executed successfully, get the failure description if unsuccessful, etc.
 * <p/>
 * <b>Example:</b> Read the server state
 * <pre>
 *     <code>
 *
 *          final ModelControllerClient client = ModelControllerClient.Factory.create(hostname, port);
 *          final ModelNode address = new ModelNode().setEmptyList();
 *          // Read the server state
 *          final ModelNode op = Operations.createReadAttributeOperation(address, "server-state");
 *          final ModelNode result = client.execute(op);
 *          if (Operations.isSuccessfulOutcome(result)) {
 *              System.out.printf("Server state: %s%n", Operations.readResult(result));
 *          } else {
 *              System.out.printf("Failure! %s%n", Operations.getFailureDescription(result));
 *          }
 *     </code>
 * </pre>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Operations {

    /**
     * Checks the result for a successful operation outcome.
     *
     * @param outcome the result of executing an operation
     *
     * @return {@code true} if the operation was successful, otherwise {@code false}
     */
    public static boolean isSuccessfulOutcome(final ModelNode outcome) {
        return outcome.get(OUTCOME).asString().equals(SUCCESS);
    }

    /**
     * Parses the result and returns the failure description.
     *
     * @param result the result of executing an operation
     *
     * @return the failure description if defined, otherwise a new undefined model node
     *
     * @throws IllegalArgumentException if the outcome of the operation was successful
     */
    public static ModelNode getFailureDescription(final ModelNode result) {
        if (isSuccessfulOutcome(result)) {
            throw ControllerClientLogger.ROOT_LOGGER.noFailureDescription();
        }
        if (result.hasDefined(FAILURE_DESCRIPTION)) {
            return result.get(FAILURE_DESCRIPTION);
        }
        return new ModelNode();
    }

    /**
     * Creates an {@linkplain ModelNode address} that can be used as the address for an operation. The address is
     * simply a {@link ModelNode} of type {@link ModelType#LIST}.
     * <p>
     * The string is split into key/value pairs. If the final key does not have a value an {@code *} is used to
     * indicate a  wildcard for the address.
     * </p>
     *
     * @param pairs the key/value pairs to use
     *
     * @return an address for the key/value pairs
     */
    public static ModelNode createAddress(final String... pairs) {
        return createAddress(Arrays.asList(pairs));
    }

    /**
     * Creates an {@linkplain ModelNode address} that can be used as the address for an operation. The address is
     * simply a {@link ModelNode} of type {@link ModelType#LIST}.
     * <p>
     * The string is split into key/value pairs. If the final key does not have a value an {@code *} is used to
     * indicate a  wildcard for the address.
     * </p>
     *
     * @param pairs the key/value pairs to use
     *
     * @return an address for the key/value pairs
     */
    public static ModelNode createAddress(final Iterable<String> pairs) {
        final ModelNode address = new ModelNode();
        final Iterator<String> iterator = pairs.iterator();
        while (iterator.hasNext()) {
            final String key = iterator.next();
            final String value = (iterator.hasNext() ? iterator.next() : "*");
            address.add(key, value);
        }
        return address;
    }

    /**
     * Returns the address for the operation.
     *
     * @param op the operation
     *
     * @return the operation address or a new undefined model node
     */
    public static ModelNode getOperationAddress(final ModelNode op) {
        return op.hasDefined(OP_ADDR) ? op.get(OP_ADDR) : new ModelNode();
    }

    /**
     * Returns the name of the operation.
     *
     * @param op the operation
     *
     * @return the name of the operation
     *
     * @throws IllegalArgumentException if the operation was not defined.
     */
    public static String getOperationName(final ModelNode op) {
        if (op.hasDefined(OP)) {
            return op.get(OP).asString();
        }
        throw ControllerClientLogger.ROOT_LOGGER.operationNameNotFound();
    }

    /**
     * Creates an add operation.
     *
     * @param address the address for the operation
     *
     * @return the operation
     */
    public static ModelNode createAddOperation(final ModelNode address) {
        return createOperation(ADD, address);
    }

    /**
     * Creates a remove operation.
     *
     * @param address the address for the operation
     *
     * @return the operation
     */
    public static ModelNode createRemoveOperation(final ModelNode address) {
        return createOperation(REMOVE_OPERATION, address);
    }

    /**
     * Creates a composite operation with an empty address and empty steps that will rollback on a runtime failure.
     * <p/>
     * By default the {@link ClientConstants#ROLLBACK_ON_RUNTIME_FAILURE} is set to {@code true} to rollback all
     * operations if one fails.
     *
     * @return the operation
     */
    public static ModelNode createCompositeOperation() {
        final ModelNode op = createOperation(COMPOSITE);
        op.get(ROLLBACK_ON_RUNTIME_FAILURE).set(true);
        op.get(STEPS).setEmptyList();
        return op;
    }

    /**
     * Creates an operation to read the attribute represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the read attribute for
     * @param attributeName the name of the parameter to read
     *
     * @return the operation
     */
    public static ModelNode createReadAttributeOperation(final ModelNode address, final String attributeName) {
        final ModelNode op = createOperation(READ_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * Creates a non-recursive operation to read a resource.
     *
     * @param address the address to create the read for
     *
     * @return the operation
     */
    public static ModelNode createReadResourceOperation(final ModelNode address) {
        return createOperation(READ_RESOURCE_OPERATION, address);
    }

    /**
     * Creates an operation to read a resource.
     *
     * @param address   the address to create the read for
     * @param recursive whether to search recursively or not
     *
     * @return the operation
     */
    public static ModelNode createReadResourceOperation(final ModelNode address, final boolean recursive) {
        final ModelNode op = createOperation(READ_RESOURCE_OPERATION, address);
        op.get(RECURSIVE).set(recursive);
        return op;
    }

    /**
     * Creates an operation to undefine an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name attribute to undefine
     *
     * @return the operation
     */
    public static ModelNode createUndefineAttributeOperation(final ModelNode address, final String attributeName) {
        final ModelNode op = createOperation(UNDEFINE_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final boolean value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final int value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final long value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final String value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     *
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final ModelNode value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates a generic operation with an empty (root) address.
     *
     * @param operation the operation to create
     *
     * @return the operation
     */
    public static ModelNode createOperation(final String operation) {
        final ModelNode op = new ModelNode();
        op.get(OP).set(operation);
        op.get(OP_ADDR).setEmptyList();
        return op;
    }

    /**
     * Creates an operation.
     *
     * @param operation the operation name
     * @param address   the address for the operation
     *
     * @return the operation
     *
     * @throws IllegalArgumentException if the address is not of type {@link org.jboss.dmr.ModelType#LIST}
     */
    public static ModelNode createOperation(final String operation, final ModelNode address) {
        if (address.getType() != ModelType.LIST) {
            throw ControllerClientLogger.ROOT_LOGGER.invalidAddressType();
        }
        final ModelNode op = createOperation(operation);
        op.get(OP_ADDR).set(address);
        return op;
    }

    /**
     * Reads the result of an operation and returns the result. If the operation does not have a {@link
     * ClientConstants#RESULT} attribute, a new undefined {@link org.jboss.dmr.ModelNode} is returned.
     *
     * @param result the result of executing an operation
     *
     * @return the result of the operation or a new undefined model node
     */
    public static ModelNode readResult(final ModelNode result) {
        return (result.hasDefined(RESULT) ? result.get(RESULT) : new ModelNode());
    }

    private static ModelNode createNoValueWriteOperation(final ModelNode address, final String attributeName) {
        final ModelNode op = createOperation(WRITE_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * A builder for building composite operations.
     * <p/>
     *
     * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
     */
    public static class CompositeOperationBuilder extends OperationBuilder {
        private final ModelNode op;

        private CompositeOperationBuilder(final ModelNode op) {
            super(op);
            this.op = op;
        }

        private CompositeOperationBuilder(final ModelNode op, final boolean autoCloseStreams) {
            super(op, autoCloseStreams);
            this.op = op;
        }

        /**
         * Creates a new builder.
         *
         * @return a new builder
         */
        public static CompositeOperationBuilder create() {
            return new CompositeOperationBuilder(createCompositeOperation());
        }

        /**
         * Creates a new builder.
         *
         * @param autoCloseStreams whether streams should be automatically closed
         *
         * @return a new builder
         */
        public static CompositeOperationBuilder create(final boolean autoCloseStreams) {
            return new CompositeOperationBuilder(createCompositeOperation(), autoCloseStreams);
        }

        /**
         * Adds a new operation to the composite operation.
         * <p/>
         * Note that subsequent calls after a {@link #build() build} invocation will result the operation being
         * appended to and could result in unexpected behaviour.
         *
         * @param op the operation to add
         *
         * @return the current builder
         */
        public CompositeOperationBuilder addStep(final ModelNode op) {
            this.op.get(STEPS).add(op);
            return this;
        }

        @Override
        public CompositeOperationBuilder addFileAsAttachment(final File file) {
            super.addFileAsAttachment(file);
            return this;
        }

        @Override
        public CompositeOperationBuilder addInputStream(final InputStream in) {
            super.addInputStream(in);
            return this;
        }
    }
}
