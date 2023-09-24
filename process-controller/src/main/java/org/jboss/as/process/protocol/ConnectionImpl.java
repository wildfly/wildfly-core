/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process.protocol;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.Executor;

import org.jboss.as.process.logging.ProcessLogger;
import org.wildfly.common.Assert;
import org.wildfly.common.ref.CleanerReference;
import org.wildfly.common.ref.Reaper;
import org.wildfly.common.ref.Reference;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ConnectionImpl implements Connection {

    private static final Reaper<MessageOutputStream, OutputStreamCloser> REAPER = new Reaper<MessageOutputStream, OutputStreamCloser>() {
        @Override
        public void reap(Reference<MessageOutputStream, OutputStreamCloser> reference) {
            reference.getAttachment().cleanup();
        }
    };

    private final Socket socket;

    private final Object lock = new Object();

    // protected by {@link #lock}
    // This isn't actually the MessageOutputStream object that is sending; it's just a marker
    // that MOS instances use to track if they are the current sender for the connection
    private Object sender;
    // protected by {@link #lock}
    private boolean readDone;
    // protected by {@link #lock}
    private boolean writeDone;

    private volatile MessageHandler messageHandler;

    private final Executor readExecutor;

    private volatile Object attachment;

    private volatile MessageHandler backupHandler;

    private final ClosedCallback callback;

    ConnectionImpl(final Socket socket, final MessageHandler handler, final Executor readExecutor, final ClosedCallback callback) {
        this.socket = socket;
        messageHandler = handler;
        this.readExecutor = readExecutor;
        this.callback = callback;
    }

    @Override
    public OutputStream writeMessage() throws IOException {
        final OutputStream os;
        synchronized (lock) {
            if (writeDone) {
                throw ProcessLogger.ROOT_LOGGER.writesAlreadyShutdown();
            }
            while (sender != null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException();
                }
            }
            boolean ok = false;
            try {
                MessageOutputStream mos = new MessageOutputStream();
                // Use a PhantomReference instead of overriding finalize() to ensure close gets called
                // CleanerReference handles ensuring there's a strong ref to itself so we can just construct it and move on
                new CleanerReference<MessageOutputStream, OutputStreamCloser>(mos, mos.closer, REAPER);
                sender = mos.closer;
                os = new BufferedOutputStream(mos);
                ok = true;
            } finally {
                if (! ok) {
                    // let someone else try
                    lock.notify();
                }
            }
        }
        return os;
    }

    @Override
    public void shutdownWrites() throws IOException {
        synchronized (lock) {
            if (writeDone) return;
            while (sender != null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException();
                }
            }
            writeDone = true;
            if (readDone) {
                socket.close();
            } else {
                socket.shutdownOutput();
            }
            lock.notifyAll();
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            lock.notifyAll();
            sender = null;
            readDone = true;
            writeDone = true;
            socket.close();
            lock.notifyAll();
        }
    }

    @Override
    public void setMessageHandler(final MessageHandler messageHandler) {
        Assert.checkNotNullParam("messageHandler", messageHandler);
        this.messageHandler = messageHandler;
    }

    @Override
    public InetAddress getPeerAddress() {
        synchronized (lock) {
            final Socket socket = this.socket;
            if (socket != null) {
                return socket.getInetAddress();
            } else {
                return null;
            }
        }
    }

    @Override
    public void attach(final Object attachment) {
        this.attachment = attachment;
    }

    @Override
    public Object getAttachment() {
        return attachment;
    }

    @Override
    public void backupMessageHandler() {
        backupHandler = messageHandler;
    }

    @Override
    public void restoreMessageHandler() {
        MessageHandler handler = backupHandler;
        setMessageHandler(handler == null ? MessageHandler.NULL : handler);
    }

    Runnable getReadTask() {
        return new Runnable() {
            @Override
            public void run() {
                boolean closed = false;
                OutputStream mos = null;
                try {
                    Pipe pipe = null;
                    final InputStream is = socket.getInputStream();
                    final int bufferSize = 8192;
                    final byte[] buffer = new byte[bufferSize];
                    for (;;) {

                        int cmd = is.read();
                        switch (cmd) {
                            case -1: {
                                ProcessLogger.PROTOCOL_CONNECTION_LOGGER.trace("Received end of stream");
                                // end of stream
                                safeHandleShutdown();
                                boolean done;
                                if (mos != null) {
                                    mos.close();
                                    pipe.await();
                                }
                                synchronized (lock) {
                                    readDone = true;
                                    done = writeDone;
                                }
                                if (done) {
                                    StreamUtils.safeClose(socket);
                                    safeHandleFinished();
                                }
                                closed = true;
                                closed();
                                return;
                            }
                            case ProtocolConstants.CHUNK_START: {
                                if (mos == null) {
                                    pipe = new Pipe(8192);
                                    // new message!
                                    final InputStream pis = pipe.getIn();
                                    mos = pipe.getOut();

                                    readExecutor.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            safeHandleMessage(new MessageInputStream(pis));
                                        }
                                    });
                                }
                                int cnt = StreamUtils.readInt(is);
                                ProcessLogger.PROTOCOL_CONNECTION_LOGGER.tracef("Received data chunk of size %d", Integer.valueOf(cnt));
                                while (cnt > 0) {
                                    int sc = is.read(buffer, 0, Math.min(cnt, bufferSize));
                                    if (sc == -1) {
                                        throw ProcessLogger.ROOT_LOGGER.unexpectedEndOfStream();
                                    }
                                    mos.write(buffer, 0, sc);
                                    cnt -= sc;
                                }
                                break;
                            }
                            case ProtocolConstants.CHUNK_END: {
                                ProcessLogger.PROTOCOL_CONNECTION_LOGGER.trace("Received end data marker");
                                if (mos != null) {
                                    // end message
                                    mos.close();
                                    pipe.await();
                                    mos = null;
                                    pipe = null;
                                }
                                break;
                            }
                            default: {
                                throw ProcessLogger.ROOT_LOGGER.invalidCommandByte(cmd);
                            }
                        }
                    }
                } catch (IOException e) {
                    safeHandlerFailure(e);
                } finally {
                    StreamUtils.safeClose(mos);
                    if (!closed) {
                        closed();
                    }
                }
            }
        };
    }

    void safeHandleMessage(final InputStream pis) {
        try {
            messageHandler.handleMessage(this, pis);
        } catch (RuntimeException e) {
            ProcessLogger.PROTOCOL_CONNECTION_LOGGER.failedToReadMessage(e);
        } catch (IOException e) {
            ProcessLogger.PROTOCOL_CONNECTION_LOGGER.failedToReadMessage(e);
        } catch (NoClassDefFoundError e) {
            ProcessLogger.PROTOCOL_CONNECTION_LOGGER.failedToReadMessage(e);
        } catch (Error e) {
            ProcessLogger.PROTOCOL_CONNECTION_LOGGER.failedToReadMessage(e);
            throw e;
        } finally {
            StreamUtils.safeClose(pis);
        }
    }

    void safeHandleShutdown() {
        try {
            messageHandler.handleShutdown(this);
        } catch (IOException e) {
            ProcessLogger.PROTOCOL_CONNECTION_LOGGER.failedToHandleSocketShutdown(e);
        }
    }

    void safeHandleFinished() {
        try {
            messageHandler.handleFinished(this);
        } catch (IOException e) {
            ProcessLogger.PROTOCOL_CONNECTION_LOGGER.failedToHandleSocketFinished(e);
        }
    }

    void safeHandlerFailure(IOException e) {
        try {
            messageHandler.handleFailure(this, e);
        } catch (IOException e1) {
            ProcessLogger.PROTOCOL_CONNECTION_LOGGER.failedToHandleSocketFailure(e);
        }
    }

    final class MessageInputStream extends FilterInputStream {

        protected MessageInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            try {
                while (in.read() != -1) {}
            } finally {
                super.close();
            }
        }
    }

    final class MessageOutputStream extends FilterOutputStream {

        private final byte[] hdr = new byte[5];
        private final OutputStreamCloser closer;

        MessageOutputStream() throws IOException {
            this(socket.getOutputStream());
        }

        private MessageOutputStream(OutputStream out) {
            super(out);
            this.closer = new OutputStreamCloser(out);
        }

        @Override
        public void write(final int b) throws IOException {
            throw new IllegalStateException();
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            if (len == 0) {
                return;
            }
            final byte[] hdr = this.hdr;
            hdr[0] = (byte) ProtocolConstants.CHUNK_START;
            hdr[1] = (byte) (len >> 24);
            hdr[2] = (byte) (len >> 16);
            hdr[3] = (byte) (len >> 8);
            hdr[4] = (byte) (len >> 0);
            synchronized (lock) {
                if (sender != closer || writeDone) {
                    if (sender == closer) sender = null;
                    lock.notifyAll();
                    throw ProcessLogger.ROOT_LOGGER.writeChannelClosed();
                }
                ProcessLogger.PROTOCOL_CONNECTION_LOGGER.tracef("Sending data chunk of size %d", Integer.valueOf(len));
                out.write(hdr);
                out.write(b, off, len);
            }
        }

        @Override
        public void close() throws IOException {
            closer.close();
        }
    }

    private final class OutputStreamCloser implements Closeable {

        private final OutputStream out;

        private OutputStreamCloser(OutputStream out) {
            this.out = out;
        }

        @Override
        public void close() throws IOException {
            synchronized (lock) {
                if (sender != this) {
                    return;
                }
                sender = null;
                // wake up waiters
                lock.notify();
                if (writeDone) throw ProcessLogger.ROOT_LOGGER.writeChannelClosed();
                if (readDone) {
                    readExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            safeHandleFinished();
                        }
                    });
                }
                ProcessLogger.PROTOCOL_CONNECTION_LOGGER.tracef("Sending end of message");
                out.write(ProtocolConstants.CHUNK_END);
            }
        }

        private void cleanup() {
            synchronized (lock) {
                if (sender == this) {
                    ProcessLogger.PROTOCOL_CONNECTION_LOGGER.leakedMessageOutputStream();
                    try {
                        close();
                    } catch (IOException e) {
                        // ignored, same as a finalizer failure would be ignored by the VM
                    }
                }
            }
        }
    }

    private void closed() {
        ClosedCallback callback = this.callback;
        if (callback != null) {
            callback.connectionClosed();
        }
    }
}
