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

package org.jboss.as.controller.remote;

import static org.xnio.IoUtils.safeClose;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.impl.ModelControllerProtocol;
import org.jboss.as.protocol.mgmt.AbstractManagementRequest;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.as.protocol.mgmt.FlushableDataOutput;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.as.protocol.mgmt.ProtocolUtils;
import org.jboss.dmr.ModelNode;

/**
 * An attachment proxy, lazily initializing the streams.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class OperationAttachmentsProxy implements Operation {

    private final ModelNode operation;
    private final List<ProxiedInputStream> proxiedStreams;

    private OperationAttachmentsProxy(final ModelNode operation, final List<ProxiedInputStream> proxiedStreams) {
        this.operation = operation;
        this.proxiedStreams = proxiedStreams;
    }

    static OperationAttachmentsProxy create(final ModelNode operation, final ManagementChannelAssociation channelAssociation, final int batchId, final int size) {
        return new OperationAttachmentsProxy(operation, getProxiedStreams(channelAssociation, batchId, size));
    }

    private static List<ProxiedInputStream> getProxiedStreams(final ManagementChannelAssociation channelAssociation, final int batchId, final int size) {
        List<ProxiedInputStream> proxiedStreams = new ArrayList<ProxiedInputStream>(size);
        for (int i = 0; i < size; i++) {
            proxiedStreams.add(new ProxiedInputStream(channelAssociation, batchId, i));
        }
        return proxiedStreams;
    }

    @Override
    public boolean isAutoCloseStreams() {
        return false;
    }

    @Override
    public List<InputStream> getInputStreams() {
        List<InputStream> result = new ArrayList<InputStream>();
        result.addAll(proxiedStreams);
        return Collections.unmodifiableList(result);
    }

    @Override
    public void close() throws IOException {
        //
    }

    void shutdown() {
        for (final ProxiedInputStream stream : proxiedStreams) {
            stream.shutdown(null);
        }
    }

    @Override
    public ModelNode getOperation() {
        return operation;
    }

    @SuppressWarnings("CloneDoesntCallSuperClone")
    @Override
    @Deprecated
    public final Operation clone() {
        return new OperationAttachmentsProxy(operation, proxiedStreams);
    }

    @Override
    @Deprecated
    public Operation clone(ModelNode operation) {
        return new OperationAttachmentsProxy(operation, proxiedStreams);
    }

    private static class ProxiedInputStream extends InputStream {
        static final int BUFFER_SIZE = 8192;

        private final int index;
        private final int batchId;
        private final Pipe pipe;
        private final ManagementChannelAssociation channelAssociation;

        private boolean initialized;
        private volatile Throwable error;

        ProxiedInputStream(final ManagementChannelAssociation channelAssociation, final int batchId, final int index) {
            this.channelAssociation = channelAssociation;
            this.batchId = batchId;
            this.index = index;
            pipe = new Pipe(BUFFER_SIZE);
        }

        @Override
        public int read() throws IOException {
            prepareForRead();
            return pipe.getIn().read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            prepareForRead();
            return pipe.getIn().read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            IOException ex = null;
            try {
                pipe.getOut().close();
            } catch (IOException e) {
                ex = e;
            }
            try {
                pipe.getIn().close();
            } catch (IOException e) {
                if (ex != null) {
                    ex.addSuppressed(e);
                } else {
                    ex = e;
                }
            }
            if (ex != null) {
                throw ex;
            }
        }

        private void prepareForRead() throws IOException {
            initializeBytes();
            throwIfError();
        }

        private void initializeBytes() {
            if (!initialized) {
                initialized = true;
                try {
                    final OutputStream os = pipe.getOut();
                    // Execute the async request
                    channelAssociation.executeRequest(batchId, new AbstractManagementRequest<Object, Object>() {

                        @Override
                        public byte getOperationType() {
                            return ModelControllerProtocol.GET_INPUTSTREAM_REQUEST;
                        }

                        @Override
                        protected void sendRequest(ActiveOperation.ResultHandler<Object> resultHandler, ManagementRequestContext<Object> context, FlushableDataOutput output) throws IOException {
                            output.write(ModelControllerProtocol.PARAM_INPUTSTREAM_INDEX);
                            output.writeInt(index);
                        }

                        @Override
                        public void handleRequest(DataInput input, ActiveOperation.ResultHandler<Object> resultHandler, ManagementRequestContext<Object> context) throws IOException {
                            try {
                                ProtocolUtils.expectHeader(input, ModelControllerProtocol.PARAM_INPUTSTREAM_LENGTH);
                                final int size = input.readInt();
                                ProtocolUtils.expectHeader(input, ModelControllerProtocol.PARAM_INPUTSTREAM_CONTENTS);
                                final byte[] buffer = new byte[BUFFER_SIZE];
                                int totalRead = 0;
                                while (totalRead < size) {
                                    int len = Math.min(size - totalRead, buffer.length);
                                    input.readFully(buffer, 0, len);
                                    os.write(buffer, 0, len);
                                    totalRead += len;
                                }
                                os.close();
                            } catch (IOException e) {
                                shutdown(e);
                                throw e;
                            }
                        }
                    });
                } catch (IOException e) {
                    shutdown(e);
                }
            }
        }

        private void throwIfError() throws IOException {
            if (error != null) {
                if (error instanceof IOException) {
                    throw (IOException) error;
                }
                throw new IOException(error);
            }
        }

        private void shutdown(Throwable error) {
            safeClose(this);
            this.error = error;
        }
    }
}
