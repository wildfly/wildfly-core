/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import static org.jboss.as.protocol.mgmt.ProtocolUtils.expectHeader;

import java.io.DataInput;
import java.io.IOException;

/**
 * {@link ManagementRequest} that sends a {@link ManagementProtocol#TYPE_PING} header.
 * Note that this is distinct from the top-level sending of {@link ManagementPingHeader} used
 * by legacy (community 7.0.x) clients.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ManagementPingRequest extends AbstractManagementRequest<Long, Void> {

    public static final ManagementPingRequest INSTANCE = new ManagementPingRequest();

    @Override
    public byte getOperationType() {
        return ManagementProtocol.TYPE_PING;
    }

    @Override
    protected void sendRequest(ActiveOperation.ResultHandler<Long> resultHandler, ManagementRequestContext<Void> context, FlushableDataOutput output) throws IOException {
        // nothing besides the header
    }

    @Override
    public void handleRequest(DataInput input, ActiveOperation.ResultHandler<Long> resultHandler, ManagementRequestContext<Void> managementRequestContext) throws IOException {
        expectHeader(input, ManagementProtocol.TYPE_PONG);
        long instanceID = input.readLong();
        resultHandler.done(instanceID);
        expectHeader(input, ManagementProtocol.RESPONSE_END);
    }
}
