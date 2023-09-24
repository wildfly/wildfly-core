/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelController.OperationTransaction;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProxyController.ProxyOperationControl;
import org.jboss.as.controller.ProxyOperationAddressTranslator;
import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.remote.RemoteProxyController;
import org.jboss.as.controller.remote.ResponseAttachmentInputStreamSupport;
import org.jboss.as.controller.remote.TransactionalProtocolOperationHandler;
import org.jboss.as.controller.support.RemoteChannelPairSetup;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementClientChannelStrategy;
import org.jboss.as.protocol.mgmt.support.ManagementChannelInitialization;
import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.threads.AsyncFutureTask;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class RemoteProxyControllerProtocolTestCase {

    ResponseAttachmentInputStreamSupport responseAttachmentSupport;
    RemoteChannelPairSetup channels;

    @Before
    public void start() throws Exception {
        responseAttachmentSupport = new ResponseAttachmentInputStreamSupport();
    }

    @After
    public void stop() throws Exception {
        channels.stopChannels();
        channels.shutdownRemoting();
        channels = null;
        responseAttachmentSupport.shutdown();
        responseAttachmentSupport = null;
    }

    @Test @Ignore("WFCORE-1125")
    public void testOperationMessageHandler() throws Exception {
        final MockModelController controller = new MockModelController() {
            @Override
            public ModelNode execute(ModelNode operation, OperationMessageHandler handler, ModelController.OperationTransactionControl control, OperationAttachments attachments) {
                this.operation = operation;
                handler.handleReport(MessageSeverity.INFO, "Test1");
                handler.handleReport(MessageSeverity.INFO, "Test2");
                control.operationPrepared(new OperationTransaction() {

                    @Override
                    public void rollback() {
                    }

                    @Override
                    public void commit() {
                    }
                }, new ModelNode());
                return new ModelNode();
            }
        };
        final RemoteProxyController proxyController = setupProxyHandlers(controller);


        ModelNode operation = new ModelNode();
        operation.get("test").set("123");

        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();

        CommitProxyOperationControl commitControl = new CommitProxyOperationControl();
        proxyController.execute(operation,
                new OperationMessageHandler() {

                    @Override
                    public void handleReport(MessageSeverity severity, String message) {
                        if (severity == MessageSeverity.INFO && message.startsWith("Test")) {
                            messages.add(message);
                        }
                    }
                },
                commitControl,
                null, null);
        Assert.assertNotNull(commitControl.tx);
        commitControl.tx.commit();
        assertEquals("123", controller.getOperation().get("test").asString());
        assertEquals("Test1", messages.take());
        assertEquals("Test2", messages.take());
    }

    @Test
    public void testOperationControlFailed() throws Exception {
        final MockModelController controller = new MockModelController() {
            @Override
            public ModelNode execute(ModelNode operation, OperationMessageHandler handler, OperationTransactionControl control, OperationAttachments attachments) {
                final ModelNode result = new ModelNode();
                result.get(OUTCOME).set(FAILED);
                result.get(FAILURE_DESCRIPTION).set("broken");
                return result;
            }
        };
        final RemoteProxyController proxyController = setupProxyHandlers(controller);

        ModelNode operation = new ModelNode();
        operation.get("test").set("123");

        final AtomicBoolean prepared = new AtomicBoolean();
        final AtomicBoolean completed = new AtomicBoolean();
        final TestFuture<ModelNode> failure = new TestFuture<>();
        proxyController.execute(operation,
                null,
                new ProxyOperationControl() {

                    @Override
                    public void operationPrepared(OperationTransaction transaction, ModelNode result) {
                        prepared.set(true);
                    }

                    @Override
                    public void operationFailed(ModelNode response) {
                        failure.done(response);
                    }

                    @Override
                    public void operationCompleted(OperationResponse response) {
                        completed.set(true);
                    }
                },
                null, null);
        ModelNode result = failure.get();
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertEquals("broken", result.get(FAILURE_DESCRIPTION).asString());
        assertFalse(prepared.get());
        assertFalse(completed.get());
    }

    @Test
    public void testOperationControlExceptionInController() throws Exception {
        final MockModelController controller = new MockModelController() {
            @Override
            public ModelNode execute(ModelNode operation, OperationMessageHandler handler, OperationTransactionControl control, OperationAttachments attachments) {
                throw new RuntimeException("Crap");
            }
        };
        final RemoteProxyController proxyController = setupProxyHandlers(controller);

        ModelNode operation = new ModelNode();
        operation.get("test").set("123");

        final AtomicBoolean prepared = new AtomicBoolean();
        final AtomicBoolean completed = new AtomicBoolean();
        final TestFuture<ModelNode> failure = new TestFuture<>();
        proxyController.execute(operation,
                null,
                new ProxyOperationControl() {

                    @Override
                    public void operationPrepared(OperationTransaction transaction, ModelNode result) {
                        prepared.set(true);
                    }

                    @Override
                    public void operationFailed(ModelNode response) {
                        failure.done(response);
                    }

                    @Override
                    public void operationCompleted(OperationResponse response) {
                        completed.set(true);
                    }
                },
                null, null);
        ModelNode result = failure.get();
        assertEquals(FAILED, result.get(OUTCOME).asString());
        assertEquals("java.lang.RuntimeException:Crap", result.get(FAILURE_DESCRIPTION).asString());
        assertFalse(prepared.get());
        assertFalse(completed.get());
    }

    @Test
    public void testTransactionCommit() throws Exception {
        final AtomicInteger txCompletionStatus = new AtomicInteger();
        final OperationTransaction tx = new OperationTransaction() {

            @Override
            public void rollback() {
                txCompletionStatus.set(1);
            }

            @Override
            public void commit() {
                txCompletionStatus.set(2);
            }
        };
        MockModelController controller = new MockModelController() {
            @Override
            public ModelNode execute(ModelNode operation, OperationMessageHandler handler, OperationTransactionControl control, OperationAttachments attachments) {

                ModelNode node = new ModelNode();
                node.get(OUTCOME).set(SUCCESS);
                node.get(RESULT).set("prepared");
                control.operationPrepared(tx, node);

                node.get(RESULT).set("final");
                return node;
            }
        };

        final RemoteProxyController proxyController = setupProxyHandlers(controller);

        ModelNode operation = new ModelNode();
        operation.get("test").set("123");

        final AtomicBoolean failed = new AtomicBoolean();
        final TestFuture<ModelNode> prepared = new TestFuture<ModelNode>();
        final TestFuture<OperationTransaction> preparedTx = new TestFuture<OperationTransaction>();
        final TestFuture<OperationResponse> result = new TestFuture<>();
        proxyController.execute(operation,
                null,
                new ProxyOperationControl() {

                    @Override
                    public void operationPrepared(OperationTransaction transaction, ModelNode result) {
                        prepared.done(result);
                        preparedTx.done(transaction);
                    }

                    @Override
                    public void operationFailed(ModelNode response) {
                        failed.set(true);
                    }

                    @Override
                    public void operationCompleted(OperationResponse response) {
                        result.done(response);
                    }
                },
                null, null);

        ModelNode preparedResult = prepared.get();
        assertEquals(SUCCESS, preparedResult.get(OUTCOME).asString());
        assertEquals("prepared", preparedResult.get(RESULT).asString());
        assertFalse(failed.get());
        assertFalse(result.isDone());
        preparedTx.get().commit();

        ModelNode finalResult = result.get().getResponseNode();
        assertEquals(SUCCESS, finalResult.get(OUTCOME).asString());
        assertEquals("final", finalResult.get(RESULT).asString());
        assertEquals(2, txCompletionStatus.get());
    }

    @Test
    public void testTransactionRollback() throws Exception {
        final AtomicInteger txCompletionStatus = new AtomicInteger();
        final OperationTransaction tx = new OperationTransaction() {

            @Override
            public void rollback() {
                txCompletionStatus.set(1);
            }

            @Override
            public void commit() {
                txCompletionStatus.set(2);
            }
        };
        MockModelController controller = new MockModelController() {
            @Override
            public ModelNode execute(ModelNode operation, OperationMessageHandler handler, OperationTransactionControl control, OperationAttachments attachments) {

                ModelNode node = new ModelNode();
                node.get(OUTCOME).set(SUCCESS);
                node.get(RESULT).set("prepared");
                control.operationPrepared(tx, node);

                return node;
            }
        };

        final RemoteProxyController proxyController = setupProxyHandlers(controller);

        ModelNode operation = new ModelNode();
        operation.get("test").set("123");

        final AtomicBoolean failed = new AtomicBoolean();
        final TestFuture<ModelNode> prepared = new TestFuture<ModelNode>();
        final TestFuture<OperationTransaction> preparedTx = new TestFuture<OperationTransaction>();
        final TestFuture<OperationResponse> result = new TestFuture<>();
        proxyController.execute(operation,
                null,
                new ProxyOperationControl() {

                    @Override
                    public void operationPrepared(OperationTransaction transaction, ModelNode result) {
                        prepared.done(result);
                        preparedTx.done(transaction);
                    }

                    @Override
                    public void operationFailed(ModelNode response) {
                        failed.set(true);
                    }

                    @Override
                    public void operationCompleted(OperationResponse response) {
                        result.done(response);
                    }
                },
                null, null);

        ModelNode preparedResult = prepared.get();
        assertEquals(SUCCESS, preparedResult.get(OUTCOME).asString());
        assertEquals("prepared", preparedResult.get(RESULT).asString());
        assertFalse(failed.get());
        assertFalse(result.isDone());
        preparedTx.get().rollback();
        assertEquals(SUCCESS, result.get().getResponseNode().get(OUTCOME).asString());
        assertEquals("prepared", result.get().getResponseNode().get(RESULT).asString());
        assertEquals(1, txCompletionStatus.get());
    }

    @Test
    public void testFailAfterPrepare() throws Exception {
        final ModelNode node = new ModelNode();
        final ModelController controller = new MockModelController() {
            @Override
            public ModelNode execute(ModelNode operation, OperationMessageHandler handler, OperationTransactionControl control, OperationAttachments attachments) {
                control.operationPrepared(new OperationTransaction() {
                    @Override
                    public void commit() {
                        //
                    }

                    @Override
                    public void rollback() {
                        //
                    }
                }, node);
                // Fail after the commit or rollback was called
                throw new IllegalStateException();
            }
        };
        final ModelNode result = new ModelNode();
        final RemoteProxyController proxyController = setupProxyHandlers(controller);
        final CommitProxyOperationControl commitControl = new CommitProxyOperationControl() {
            @Override
            public void operationCompleted(OperationResponse response) {
                super.operationCompleted(response);
                result.set(response.getResponseNode());
            }
        };
        proxyController.execute(node, null, commitControl, null, null);
        commitControl.tx.commit();
        // Needs to call operation-completed
        Assert.assertEquals(2, commitControl.txCompletionStatus.get());
        Assert.assertTrue(result.isDefined());
        Assert.assertEquals("failed", result.get("outcome").asString());
        Assert.assertTrue(result.hasDefined("failure-description"));
    }

    @Test
    public void testAttachmentInputStreams() throws Exception {

        final byte[] firstBytes = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        final byte[] secondBytes = new byte[] {10, 9, 8 , 7 , 6, 5, 4, 3, 2, 1};

        final AtomicInteger size = new AtomicInteger();
        final AtomicReference<byte[]> firstResult = new AtomicReference<byte[]>();
        final AtomicReference<byte[]> secondResult = new AtomicReference<byte[]>();
        final AtomicReference<byte[]> thirdResult = new AtomicReference<byte[]>();
        MockModelController controller = new MockModelController() {
            @Override
            public ModelNode execute(ModelNode operation, OperationMessageHandler handler, OperationTransactionControl control, OperationAttachments attachments) {
                int streamIndex = 0;
                for (InputStream in : attachments.getInputStreams()) {
                    try {
                        ArrayList<Integer> readBytes = new ArrayList<Integer>();
                        int b = in.read();
                        while (b != -1) {
                            readBytes.add(b);
                            b = in.read();
                        }

                        byte[] bytes = new byte[readBytes.size()];
                        for (int i = 0 ; i < bytes.length ; i++) {
                            bytes[i] = (byte)readBytes.get(i).intValue();
                        }

                        if (streamIndex == 0) {
                            firstResult.set(bytes);
                        } else if (streamIndex == 1) {
                            secondResult.set(bytes);
                        } else if (streamIndex == 2) {
                            thirdResult.set(bytes);
                        }
                        streamIndex++;

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                size.set(streamIndex);
                control.operationPrepared(new OperationTransaction() {

                    @Override
                    public void rollback() {
                    }

                    @Override
                    public void commit() {
                    }
                }, new ModelNode());
                return new ModelNode();
            }
        };

        final RemoteProxyController proxyController = setupProxyHandlers(controller);

        ModelNode operation = new ModelNode();
        operation.get("test").set("123");

        OperationAttachments attachments = new OperationAttachments() {

            @Override
            public List<InputStream> getInputStreams() {
                ArrayList<InputStream> streams = new ArrayList<InputStream>();
                streams.add(new ByteArrayInputStream(firstBytes));
                streams.add(new ByteArrayInputStream(secondBytes));
                streams.add(null);
                return streams;
            }

            @Override
            public boolean isAutoCloseStreams() {
                return false;
            }

            @Override
            public void close() throws IOException {
                //
            }
        };

        CommitProxyOperationControl commitControl = new CommitProxyOperationControl();
        proxyController.execute(operation,
                null,
                commitControl,
                attachments, null);
        Assert.assertNotNull(commitControl.tx);
        commitControl.tx.commit();
        assertEquals(3, size.get());
        assertArrays(firstBytes, firstResult.get());
        assertArrays(secondBytes, secondResult.get());
        assertArrays(new byte[0], thirdResult.get());
    }

    @Test
    public void testClosesBeforePrepare() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Exception> errorRef = new AtomicReference<Exception>();
        MockModelController controller = new MockModelController() {
            @Override
            public ModelNode execute(ModelNode operation, OperationMessageHandler handler, OperationTransactionControl control, OperationAttachments attachments) {
                try {
                    channels.getClientChannel().closeAsync();
                    channels.getClientChannel().awaitClosed();
                } catch (InterruptedException e) {
                    // closing a channel will cancel the controller.execute()
                } catch (Exception e) {
                    errorRef.set(e);
                } finally {
                    latch.countDown();
                    // Ensure the channels are closed
                    IoUtils.safeClose(channels.getClientChannel());
                }
                return new ModelNode();
            }
        };

        final RemoteProxyController proxyController = setupProxyHandlers(controller);

        ModelNode operation = new ModelNode();
        operation.get("test").set("123");

        CommitProxyOperationControl commitControl = new CommitProxyOperationControl();
        proxyController.execute(operation, OperationMessageHandler.DISCARD, commitControl, OperationAttachments.EMPTY, null);
        Assert.assertNull(errorRef.get());
        latch.await(15, TimeUnit.SECONDS);
        Assert.assertEquals(1, commitControl.txCompletionStatus.get());
    }

    private void assertArrays(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0 ; i < expected.length ; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }

    private RemoteProxyController setupProxyHandlers(final ModelController proxiedController) {
        try {
            channels = new RemoteChannelPairSetup();
            channels.setupRemoting(new ManagementChannelInitialization() {
                @Override
                public ManagementChannelHandler startReceiving(Channel channel) {
                    final ManagementClientChannelStrategy strategy = ManagementClientChannelStrategy.create(channel);
                    final ManagementChannelHandler support = new ManagementChannelHandler(strategy, channels.getExecutorService());
                    support.addHandlerFactory(new TransactionalProtocolOperationHandler(proxiedController, support, responseAttachmentSupport));
                    channel.addCloseHandler(new CloseHandler<Channel>() {
                        @Override
                        public void handleClose(Channel closed, IOException exception) {
                            support.shutdownNow();
                        }
                    });
                    channel.receiveMessage(support.getReceiver());
                    return support;
                }
            });
            channels.startClientConnetion();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        final Channel clientChannel = channels.getClientChannel();
        final ManagementClientChannelStrategy strategy = ManagementClientChannelStrategy.create(clientChannel);
        final ManagementChannelHandler support = new ManagementChannelHandler(strategy, channels.getExecutorService());
        final RemoteProxyController proxyController = RemoteProxyController.create(support, PathAddress.pathAddress(), ProxyOperationAddressTranslator.HOST);
        clientChannel.addCloseHandler(new CloseHandler<Channel>() {
            @Override
            public void handleClose(Channel closed, IOException exception) {
                support.shutdownNow();
            }
        });
        clientChannel.receiveMessage(support.getReceiver());
        return proxyController;
    }

    private abstract static class MockModelController extends org.jboss.as.controller.MockModelController {
        protected volatile ModelNode operation;

        ModelNode getOperation() {
            return operation;
        }
    }

    private static class CommitProxyOperationControl implements ProxyOperationControl {
        final AtomicInteger txCompletionStatus = new AtomicInteger(-1);
        OperationTransaction tx;
        @Override
        public void operationPrepared(OperationTransaction transaction, ModelNode result) {
            //DO not call commit from here
            tx = transaction;
        }

        @Override
        public void operationFailed(ModelNode response) {
            if(! txCompletionStatus.compareAndSet(-1, 1)) {
                throw new IllegalStateException();
            }
        }

        @Override
        public void operationCompleted(OperationResponse response) {
            if(! txCompletionStatus.compareAndSet(-1, 2)) {
                throw new IllegalStateException();
            }
        }

    }

    private static class TestFuture<T> extends AsyncFutureTask<T>{
        protected TestFuture() {
            super(null);
        }

        void done(T result) {
            super.setResult(result);
        }
    }

}
