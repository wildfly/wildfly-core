/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.jboss.dmr.ModelNode;

/**
 * A response to a management request, incorporating a {@link org.jboss.dmr.ModelNode} containing
 * the detyped response, along with zero or more input streams that may have been associated with
 * the response.
 * <p>
 * <strong>Streams must be consumed promptly once a response is obtained.</strong>  To prevent
 * resource leaks, the server side will close its side of the stream approximately 30 seconds
 * after the latter of its transmittal of the {@code OperationResponse} or any remote read of one of its
 * associated streams.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public interface OperationResponse extends Closeable {

    /**
     * An additional stream, besides the normal {@link #getResponseNode() response ModelNode} that is
     * associated with the response.
     */
    interface StreamEntry extends Closeable {

        /**
         * Gets the unique identifier for this stream. Meant to be unique within the context of the operation.
         * @return the id. Will not be {@code null}
         */
        String getUUID();

        /**
         * Gets the MIME type of the stream
         * @return the mime type. Cannot be {@code null}
         */
        String getMimeType();

        /**
         * Gets the underlying stream.
         */
        InputStream getStream();

        /**
         * Closes the underlying stream.
         *
         * {@inheritDoc}
         */
        @Override
        void close() throws IOException;
    }

    /**
     * Gets the DMR response to the operation.
     *
     * @return the response. Will not be {@code null}
     */
    ModelNode getResponseNode();

    /**
     * Gets any streams that were associated with the operation response. Streams will
     * be in the order in which they were attached, but callers should exercise caution
     * when making assumptions about that order if the operation executes across multiple
     * servers in a managed domain. Aspects of domain execution often occur concurrently
     * so streams may not be associated with the response in the order in which steps are
     * listed in a multistep operation.
     * <p>
     *
     * @return the streams. Will not be {@code null} but may be empty
     */
    List<StreamEntry> getInputStreams();

    /**
     * Gets a stream associated with the response that has the given
     * {@link org.jboss.as.controller.client.OperationResponse.StreamEntry#getUUID() uuid}.
     * <p>
     * Server side operation step handlers that associate a stream with a response should provide the
     * stream's uuid as the step's {@code result} value in the {@link #getResponseNode() DMR response}.
     *
     * @param uuid the uuid. Cannot be {@code null}
     *
     * @return the stream entry, or {@code null} if no entry with the given uuid is associated
     */
    StreamEntry getInputStream(String uuid);

    /**
     * Closes any {@link #getInputStreams() associated stream entries}.
     *
     * {@inheritDoc}
     */
    @Override
    void close() throws IOException;

    class Factory {
        public static OperationResponse createSimple(final ModelNode responseNode) {
            return new OperationResponse() {
                @Override
                public ModelNode getResponseNode() {
                    return responseNode;
                }

                @Override
                public List<StreamEntry> getInputStreams() {
                    return Collections.emptyList();
                }

                @Override
                public StreamEntry getInputStream(String uuid) {
                    return null;
                }

                @Override
                public void close() throws IOException {
                    //
                }
            };
        }

    }
}
