/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client;

import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

class OperationImpl implements Operation {

    private final boolean autoCloseStreams;
    private final ModelNode operation;
    private final List<InputStream> inputStreams;

    OperationImpl(final ModelNode operation, final List<InputStream> inputStreams) {
        this(operation, inputStreams, false);
    }

    OperationImpl(final ModelNode operation, final List<InputStream> inputStreams, final boolean autoCloseStreams) {
        this.operation = operation;
        this.inputStreams = inputStreams;
        this.autoCloseStreams = autoCloseStreams;
    }

    @Override
    public boolean isAutoCloseStreams() {
        return autoCloseStreams;
    }

    @Override
    public ModelNode getOperation() {
        return operation;
    }

    @Override
    public List<InputStream> getInputStreams() {
        if (inputStreams == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(inputStreams);
    }

    @Override
    public void close() throws IOException {
        final List<InputStream> streams = getInputStreams();
        for(final InputStream stream : streams) {
            StreamUtils.safeClose(stream);
        }
    }
}
