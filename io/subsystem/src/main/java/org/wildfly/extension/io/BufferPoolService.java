/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2013, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
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
