/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.as.protocol.ProtocolConnectionUtils;
import org.jboss.as.protocol.ProtocolTimeoutHandler;
import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.ConnectionPeerIdentityContext;
import org.jboss.remoting3.DuplicateRegistrationException;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.ServiceRegistrationException;
import org.jboss.remoting3.UnknownURISchemeException;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.junit.Test;
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.xnio.Cancellable;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

/**
 * Test behavior of {@link ProtocolConnectionUtils#connectSync(ProtocolConnectionConfiguration)}.
 *
 * This test needs to mock a fair amount of remoting stuff which is unfortunate, but remoting rightly
 * protects its internals enough that it would be very difficult to simulate this with real objects.
 */
public class ConnectSyncUnitTestCase {

    @Test
    public void testSimpleSuccess() throws IOException {
        Connection connection = ProtocolConnectionUtils.connectSync(getConfiguration(IoFuture.Status.DONE));
        assertNotNull(connection);
        assertTrue(connection.isOpen());
    }

    @Test
    public void testSimpleFailure() throws IOException {
        ProtocolConnectionConfiguration configuration = getConfiguration(IoFuture.Status.FAILED);
        try {
            ProtocolConnectionUtils.connectSync(configuration);
            fail();
        } catch (ConnectException expected) {
            TestTimeoutHandler handler = (TestTimeoutHandler) configuration.getTimeoutHandler();
            assertEquals(handler.getFailure(), expected.getCause());
        }
    }

    @Test
    public void testBackgroundCancellation() throws IOException {
        ProtocolConnectionConfiguration configuration = getConfiguration(IoFuture.Status.CANCELLED);
        try {
            ProtocolConnectionUtils.connectSync(configuration);
            fail();
        } catch (ConnectException expected) {
            checkTimeoutConnectException(expected);

            // Confirm the future was cancelled.
            TestTimeoutHandler handler = (TestTimeoutHandler) configuration.getTimeoutHandler();
            assertEquals(IoFuture.Status.CANCELLED, handler.getIOFuture().getStatus());
        }
    }

    @Test
    public void testSimpleTimeout() throws IOException {
        ProtocolConnectionConfiguration configuration = getConfiguration(IoFuture.Status.WAITING);
        try {
            ProtocolConnectionUtils.connectSync(configuration);
            fail();
        } catch (ConnectException expected) {
            checkTimeoutConnectException(expected);

            // Confirm the future was cancelled
            TestTimeoutHandler handler = (TestTimeoutHandler) configuration.getTimeoutHandler();
            assertEquals(IoFuture.Status.CANCELLED, handler.getIOFuture().getStatus());
        }
    }

    @Test
    public void testTimeoutRace() throws IOException {
        ProtocolConnectionConfiguration configuration = getConfiguration(IoFuture.Status.WAITING, true, false);
        try {
            ProtocolConnectionUtils.connectSync(configuration);
            fail();
        } catch (ConnectException expected) {
            checkTimeoutConnectException(expected);

            // Confirm the late arriving connection was closed
            TestTimeoutHandler handler = (TestTimeoutHandler) configuration.getTimeoutHandler();
            // We could ask for the connection object from the handler itself, but we'll
            // retrieve it from the future just as a verification that the test fixture is
            // doing what it was meant to do.
            assertEquals(IoFuture.Status.DONE, handler.getIOFuture().getStatus()); // sanity check before blocking call to get()
            Connection lateArriving = handler.getIOFuture().get();
            assertFalse(lateArriving.isOpen());
        }
    }

    @Test
    public void testTimeoutFailure() throws IOException {
        ProtocolConnectionConfiguration configuration = getConfiguration(IoFuture.Status.WAITING, false, true);
        try {
            ProtocolConnectionUtils.connectSync(configuration);
            fail();
        } catch (ConnectException expected) {
            TestTimeoutHandler handler = (TestTimeoutHandler) configuration.getTimeoutHandler();
            assertEquals(handler.getFailure(), expected.getCause());
            assertTrue(expected.getMessage().contains("WFLYPRT0053"));
        }
    }

    private static void checkTimeoutConnectException(ConnectException expected) {
        assertNull(expected.getCause());
        assertTrue(expected.getMessage().contains("WFLYPRT0023"));
    }

    private static ProtocolConnectionConfiguration getConfiguration(IoFuture.Status initialStatus) {
        return getConfiguration(initialStatus, false, false);
    }

    private static ProtocolConnectionConfiguration getConfiguration(IoFuture.Status initialStatus, boolean completeOnRecheck, boolean failOnRecheck) {
        final FutureResult<Connection> futureResult = new FutureResult<>();
        // It seems you need to add a cancel handler if you want a
        // call to futureResult.getIoFuture().cancel() to trigger a change in the future to State.CANCELLED.
        // So add one so we can test whether cancel() has been called.
        futureResult.addCancelHandler(new Cancellable() {
            public Cancellable cancel() {
                futureResult.setCancelled();
                return this;
            }
        });
        final MockConnection connection = initialStatus == IoFuture.Status.DONE || completeOnRecheck ? new MockConnection() : null;
        final TestTimeoutHandler timeoutHandler = new TestTimeoutHandler(futureResult, initialStatus, connection, failOnRecheck);
        final MockEndpoint endpoint = new MockEndpoint(futureResult);

        ProtocolConnectionConfiguration result = ProtocolConnectionConfiguration.create(endpoint, URI.create("http://127.0.0.1"));
        result.setTimeoutHandler(timeoutHandler);
        return result;
    }

    /**
     * This is the key class in these tests. Its 'await' method gets invoked by connectSync
     * and while 'await' is executing it can set up the expected
     * state that will be seen later by connectSync, beyond it's own response.
     */
    private static class TestTimeoutHandler implements ProtocolTimeoutHandler {
        private final FutureResult<Connection> futureResult;
        private final MockConnection connection;
        private final IoFuture.Status awaitStatus;
        private final IOException failure;

        private TestTimeoutHandler(FutureResult<Connection> futureResult,
                                   IoFuture.Status awaitStatus,
                                   MockConnection connection,
                                   boolean failOnRecheck) {
            this.futureResult = futureResult;
            this.connection = connection;
            this.awaitStatus = awaitStatus;
            this.failure = awaitStatus == IoFuture.Status.FAILED || failOnRecheck ? new IOException() : null;
        }

        @Override
        public IoFuture.Status await(IoFuture<?> future, long timeoutMillis) {

            // Pretend some other thread has been or still is working in the
            // background manipulating the state of the FutureResult
            if (awaitStatus == IoFuture.Status.DONE) {
                futureResult.setResult(connection);
            } else if (awaitStatus == IoFuture.Status.FAILED) {
                futureResult.setException(failure);
            } else {
                // If we're configured for Status.CANCELLED let's not actually cancel.
                // This tests that connectSync even deals with a pathological IoFuture
                // as we can confirm that it cancels the future even if the future
                // reports itself as already cancelled
                //if (awaitStatus == IoFuture.Status.CANCELLED) {
                //    futureResult.setCancelled();
                //}

                // We're saying we are waiting. But if we were configured
                // with a result or to fail, then set that up so its seen
                // if connectSync asks again
                if (connection != null) {
                    futureResult.setResult(connection);
                } else if (failure != null) {
                    futureResult.setException(failure);
                }
            }

            // Now tell the caller the status the test driver wanted
            return awaitStatus;
        }

        Connection getConnection() {
            return connection;
        }

        IOException getFailure() {
            return failure;
        }

        IoFuture<Connection> getIOFuture() {
            return futureResult.getIoFuture();
        }
    }

    /** Endpoint impl that does nothing except provide the expected IoFuture to connect calls */
    private static class MockEndpoint implements Endpoint {

        private final FutureResult<Connection> futureResult;

        private MockEndpoint(FutureResult<Connection> futureResult) {
            this.futureResult = futureResult;
        }

        @Override
        public IoFuture<Connection> connect(URI destination, OptionMap connectOptions) {
            return futureResult.getIoFuture();
        }

        // The rest is boilerplate
        @Override
        public String getName() {
            return "test";
        }

        @Override
        public Registration registerService(String serviceType, OpenListener openListener, OptionMap optionMap) throws ServiceRegistrationException {
            return null;
        }

        @Override
        public IoFuture<ConnectionPeerIdentity> getConnectedIdentity(URI destination, SSLContext sslContext, AuthenticationConfiguration authenticationConfiguration) {
            return null;
        }

        @Override
        public IoFuture<ConnectionPeerIdentity> getConnectedIdentityIfExists(URI destination, SSLContext sslContext, AuthenticationConfiguration authenticationConfiguration) {
            return null;
        }

        @Override
        public IoFuture<Connection> connect(URI destination, OptionMap connectOptions, AuthenticationContext authenticationContext) {
            return connect(destination, connectOptions);
        }

        @Override
        public IoFuture<Connection> connect(URI destination, InetSocketAddress bindAddress, OptionMap connectOptions, AuthenticationContext authenticationContext) {
            return connect(destination, connectOptions);
        }

        @Override
        public IoFuture<Connection> connect(URI destination, InetSocketAddress bindAddress, OptionMap connectOptions, SSLContext sslContext, AuthenticationConfiguration connectionConfiguration) {
            return connect(destination, connectOptions);
        }

        @Override
        public IoFuture<Connection> connect(URI destination, OptionMap connectOptions, CallbackHandler callbackHandler) throws IOException {
            return connect(destination, connectOptions);
        }

        @Override
        public Registration addConnectionProvider(String uriScheme, ConnectionProviderFactory providerFactory, OptionMap optionMap) throws DuplicateRegistrationException, IOException {
            return null;
        }

        @Override
        public <T> T getConnectionProviderInterface(String uriScheme, Class<T> expectedType) throws UnknownURISchemeException, ClassCastException {
            return null;
        }

        @Override
        public boolean isValidUriScheme(String uriScheme) {
            return false;
        }

        @Override
        public XnioWorker getXnioWorker() {
            return null;
        }

        @Override
        public Attachments getAttachments() {
            return null;
        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public void awaitClosed() throws InterruptedException {

        }

        @Override
        public void awaitClosedUninterruptibly() {

        }

        @Override
        public void closeAsync() {

        }

        @Override
        public Key addCloseHandler(CloseHandler<? super Endpoint> handler) {
            return null;
        }

        @Override
        public boolean isOpen() {
            return false;
        }
    }

    /** Connection impl that does nothing except track if close() has been called */
    private static class MockConnection implements Connection {

        private final AtomicBoolean closed = new AtomicBoolean();


        @Override
        public void close() throws IOException {
            this.closed.set(true);
        }

        @Override
        public boolean isOpen() {
            return !this.closed.get();
        }

        // The rest is boilerplate

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public SocketAddress getPeerAddress() {
            return null;
        }

        @Override
        public SSLSession getSslSession() {
            return null;
        }

        @Override
        public IoFuture<Channel> openChannel(String serviceType, OptionMap optionMap) {
            return null;
        }

        @Override
        public String getRemoteEndpointName() {
            return null;
        }

        @Override
        public Endpoint getEndpoint() {
            return null;
        }

        @Override
        public URI getPeerURI() {
            return null;
        }

        @Override
        public String getProtocol() {
            return null;
        }

        @Override
        public SecurityIdentity getLocalIdentity() {
            return null;
        }

        @Override
        public SecurityIdentity getLocalIdentity(int id) {
            return null;
        }

        @Override
        public int getPeerIdentityId() throws AuthenticationException {
            return 0;
        }

        @Override
        public ConnectionPeerIdentity getConnectionPeerIdentity() throws SecurityException {
            return null;
        }

        @Override
        public ConnectionPeerIdentity getConnectionAnonymousIdentity() {
            return null;
        }

        @Override
        public ConnectionPeerIdentityContext getPeerIdentityContext() {
            return null;
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public boolean supportsRemoteAuth() {
            return false;
        }

        @Override
        public Attachments getAttachments() {
            return null;
        }

        @Override
        public void awaitClosed() throws InterruptedException {

        }

        @Override
        public void awaitClosedUninterruptibly() {

        }

        @Override
        public void closeAsync() {

        }

        @Override
        public Key addCloseHandler(CloseHandler<? super Connection> handler) {
            return null;
        }
    }
}
