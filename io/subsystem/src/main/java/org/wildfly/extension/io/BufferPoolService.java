/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Pool;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author Flavia Rainone
 */
public class BufferPoolService implements Service<Pool<ByteBuffer>> {
    private final Consumer<Pool<ByteBuffer>> byteBufferConsumer;
    private volatile Pool<ByteBuffer> bufferPool;
    /*<buffer-pool name="default" buffer-size="2048" buffers-per-slice="512"/>*/
    private final int bufferSize;
    private final int buffersPerSlice;
    private final boolean directBuffers;

    public BufferPoolService(final Consumer<Pool<ByteBuffer>> byteBufferConsumer, final int bufferSize, final int buffersPerSlice, final boolean directBuffers) {
        this.byteBufferConsumer = byteBufferConsumer;
        this.bufferSize = bufferSize;
        this.buffersPerSlice = buffersPerSlice;
        this.directBuffers = directBuffers;
    }

    @Override
    public void start(final StartContext context) {
        bufferPool = new ByteBufferSlicePool(directBuffers ? BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR : BufferAllocator.BYTE_BUFFER_ALLOCATOR, bufferSize, buffersPerSlice * bufferSize);
        byteBufferConsumer.accept(bufferPool);
    }

    @Override
    public void stop(final StopContext context) {
        byteBufferConsumer.accept(null);
        ((ByteBufferSlicePool) bufferPool).clean();
        bufferPool = null;
    }

    @Override
    public Pool<ByteBuffer> getValue() throws IllegalStateException, IllegalArgumentException {
        return bufferPool;
    }
}
