/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

/**
 * Encapsulates information about how long management operation execution should block
 * before timing out.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public interface BlockingTimeout {

    String SYSTEM_PROPERTY = "jboss.as.management.blocking.timeout";

    /**
     * Gets the maximum period, in ms, a local blocking call should block.
     * @return the maximum period. Will be a value greater than zero.
     */
    int getLocalBlockingTimeout();

    /**
     * Gets the maximum period, in ms, a blocking call should block waiting for a response from a remote
     * process in a managed domain. Will be longer than {@link #getLocalBlockingTimeout()} to account for delays
     * due to propagation of responses across the domain and to allow any timeout on the remote process
     * to be transmitted as a response to the local process rather than the local process timing out.
     *
     * @param targetAddress the address of the target process
     * @param proxyController the proxy controller used to direct the request to the target process
     *
     * @return the maximum period. Will be a value greater than zero.
     */
    int getProxyBlockingTimeout(PathAddress targetAddress, ProxyController proxyController);

    /**
     * Gets the maximum period, in ms, a blocking call should block waiting for a response from a set of remote
     * processes in a managed domain. Use this in cases where the responses are expected to be received in
     * parallel from a set of slave Host Controllers or servers. This value will not be impacted by previous
     * calls to {@link #timeoutDetected()}.
     *
     * @param multipleProxies {@code true} if this process is the master Host Controller and there may
     *                                    be slave Host Controllers in the middle between this process
     *                                    and the targeted remote processes.
     * @return the maximum period. Will be a value greater than zero.
     */
    int getDomainBlockingTimeout(boolean multipleProxies);

    /**
     * Notifies this object that a timeout has occurred, allowing shorter timeout values
     * to be returned from {@link #getLocalBlockingTimeout()}.
     */
    void timeoutDetected();

    /**
     * Notifies this object that a timeout has occurred when invoking on the given target,
     * allowing shorter timeouts values to be returned from {@link #getProxyBlockingTimeout(PathAddress, ProxyController)}
     */
    void proxyTimeoutDetected(PathAddress targetAddress);

    class Factory {
        static final OperationContext.AttachmentKey<BlockingTimeout> ATTACHMENT_KEY = OperationContext.AttachmentKey.create(BlockingTimeout.class);

        private Factory(){}


        /**
         * Gets any blocking timeout associated with the current context. Only usable in
         * {@link org.jboss.as.controller.OperationContext.Stage#DOMAIN}
         *
         * @param context the context. Cannot e {@code null}
         * @return the blocking timeout. Will not return {@code null}
         *
         * @throws AssertionError if {@link org.jboss.as.controller.OperationContext#getCurrentStage()} does not return {@link org.jboss.as.controller.OperationContext.Stage#DOMAIN}
         */
        public static BlockingTimeout getDomainBlockingTimeout(OperationContext context) {
            assert context.getCurrentStage() == OperationContext.Stage.DOMAIN;
            return context.getAttachment(ATTACHMENT_KEY);
        }

        /**
         * Gets any blocking timeout associated with the current context.
         *
         * @param context the context. Cannot e {@code null}
         * @return the blocking timeout. Will not return {@code null}
         */
        public static BlockingTimeout getProxyBlockingTimeout(OperationContext context) {
            return context.getAttachment(ATTACHMENT_KEY);
        }
    }
}
