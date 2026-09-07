/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import org.jboss.as.test.shared.TimeoutUtil;

/**
 * A simple log server that assumes JSON messages are being sent with a separator of {@code \n}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class JsonLogServer implements Runnable, AutoCloseable {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ExecutorService service;
    private final BlockingQueue<JsonObject> queue;
    private final CountDownLatch latch;

    private JsonLogServer() {
        service = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        queue = new LinkedBlockingQueue<>();
        latch = new CountDownLatch(1);
    }

    /**
     * Creates a new TCP listening log server.
     *
     * @param port the port to listen on
     *
     * @return the log server
     */
    public static JsonLogServer createTcpServer(final int port) {
        return new TcpServer(ServerSocketFactory.getDefault(), port);
    }

    /**
     * Creates a new SSL TCP listening log server.
     * <p>
     * This uses the {@link SSLServerSocketFactory#getDefault()} to get an SSL server socket.
     * </p>
     *
     * @param port the port to listen on
     *
     * @return the log server
     */
    public static JsonLogServer createTlsServer(final int port, final Path keystorePath, final String keystorePassword) throws Exception {
        KeyManager[] keyManagers;
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        try (InputStream in = Files.newInputStream(keystorePath)) {
            final KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(in, keystorePassword.toCharArray());
            kmf.init(ks, keystorePassword.toCharArray());
            keyManagers = kmf.getKeyManagers();
        }

        final SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(keyManagers, null, null);
        return new TcpServer(context.getServerSocketFactory(), port);
    }

    /**
     * Creates a new UDP listening log server.
     *
     * @param port the port to listen on
     *
     * @return the log server
     */
    public static JsonLogServer createUdpServer(final int port) {
        return new UdpServer(port);
    }

    /**
     * Starts the log server.
     *
     * @param timeout the timeout
     */
    public void start(final long timeout) throws InterruptedException {
        if (started.compareAndSet(false, true)) {
            service.submit(this);
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                stop();
                throw new RuntimeException(String.format("Failed to start server within %d milliseconds", timeout));
            }
        }
    }

    /**
     * Stops the log server.
     */
    public void stop() {
        started.set(false);
        queue.clear();
    }

    /**
     * Indicates whether or not the log server is running.
     *
     * @return {@code true} if the log server is running, otherwise {@code false}
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isRunning() {
        return started.get();
    }

    /**
     * Gets the next log message that was logged. This can be invoked until {@code null} is returned, however
     * {@code null} won't be returned until the timeout is reached. It is best to invoke this a known number of times
     * to not stall testing.
     *
     * @param timeout the timeout to wait for the log message to be completely logged
     *
     * @return the next log message found or {@code null} if the timeout occurred before the log message was received
     *
     * @throws InterruptedException if waiting for the log message was interrupted
     */
    public JsonObject getLogMessage(final long timeout) throws InterruptedException {
        return queue.poll(timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws Exception {
        try {
            stop();
        } finally {
            service.shutdown();
            service.awaitTermination(TimeoutUtil.adjust(10), TimeUnit.SECONDS);
        }
    }

    private static class TcpServer extends JsonLogServer {
        private final ExecutorService executor;
        private final ServerSocketFactory serverSocketFactory;
        private final int port;
        private final AtomicInteger clientCount = new AtomicInteger();
        private final Deque<EchoClient> clients = new ArrayDeque<>();
        // Guarded by this
        private ServerSocket serverSocket;

        private TcpServer(final ServerSocketFactory serverSocketFactory, final int port) {
            executor = Executors.newCachedThreadPool(r -> {
                final Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("Echo Client-" + clientCount.incrementAndGet());
                return thread;
            });
            this.serverSocketFactory = serverSocketFactory;
            this.port = port;
        }

        @Override
        public void run() {
            try {
                synchronized (this) {
                    serverSocket = serverSocketFactory.createServerSocket(port);
                    super.latch.countDown();
                }
                while (isRunning()) {
                    final EchoClient client = new EchoClient(serverSocket.accept(), super.queue);
                    synchronized (clients) {
                        clients.addLast(client);
                    }
                    executor.submit(client);
                }
            } catch (IOException e) {
                stop();
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void stop() {
            try {
                synchronized (this) {
                    uncheckedClose(serverSocket);
                    serverSocket = null;
                    clientCount.set(0);
                }
                synchronized (clients) {
                    EchoClient client;
                    while ((client = clients.pollFirst()) != null) {
                        uncheckedClose(client);
                    }
                }
            } finally {
                super.stop();
            }
        }
    }

    private static class UdpServer extends JsonLogServer {
        private final int port;
        // Guarded by this
        private DatagramSocket socket;

        private UdpServer(final int port) {
            this.port = port;
        }

        @Override
        public void run() {
            synchronized (this) {
                try {
                    socket = new DatagramSocket(port);
                    super.latch.countDown();
                } catch (SocketException e) {
                    throw new UncheckedIOException(e);
                }
            }
            try {
                while (isRunning()) {
                    final DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
                    socket.receive(packet);
                    try (JsonReader reader = Json.createReader(new ByteArrayInputStream(packet.getData()))) {
                        super.queue.offer(reader.readObject());
                    }
                }
            } catch (IOException e) {
                if (isRunning()) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public void stop() {
            try {
                synchronized (this) {
                    uncheckedClose(socket);
                    socket = null;
                }
            } finally {
                super.stop();
            }
        }
    }

    private static void uncheckedClose(final Closeable... closeables) {
        UncheckedIOException cause = null;
        for (Closeable closeable : closeables) {
            if (closeable != null) try {
                closeable.close();
            } catch (IOException e) {
                if (cause == null) {
                    cause = new UncheckedIOException(e);
                } else {
                    cause.addSuppressed(e);
                }
            }
        }
        if (cause != null) {
            throw cause;
        }
    }

    private static class EchoClient implements Closeable, Runnable {
        private final Socket socket;
        private final BlockingQueue<JsonObject> queue;
        private volatile boolean closed = false;

        private EchoClient(final Socket socket, final BlockingQueue<JsonObject> queue) {
            this.socket = socket;
            this.queue = queue;
        }

        @Override
        public void run() {
            try {
                InputStream in = socket.getInputStream();
                while (!closed) {
                    final byte[] buffer = new byte[512];
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        for (int i = 0; i < len; i++) {
                            final byte b = buffer[i];
                            if (b == '\n') {
                                // This should indicate the end of the record so we can assume we have a full JSON message
                                try (JsonReader reader = Json.createReader(new ByteArrayInputStream(out.toByteArray()))) {
                                    queue.add(reader.readObject());
                                    out.reset();
                                }
                            } else {
                                out.write(b);
                            }
                        }
                    }
                    close();
                }
            } catch (IOException e) {
                uncheckedClose(this);
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() {
            closed = true;
            uncheckedClose(socket);
        }
    }
}
