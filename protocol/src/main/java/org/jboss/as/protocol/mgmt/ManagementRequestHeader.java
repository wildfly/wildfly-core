/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * ManagementProtocol header used for management requests.  Provides the default header fields from
 * {@link ManagementProtocolHeader} as well as a field to identify who the
 * request should be handled by.
 *
 * @author John Bailey
 * @author Kabir Khan
 */
public class ManagementRequestHeader extends ManagementProtocolHeader {

    private int requestId;
    private int batchId;
    private byte operationId;
    // Actually not needed
    private boolean oneWay;

    /**
     * Construct an instance with the protocol version and operation handler for the header.
     *
     * @param version The protocol version
     * @param requestId The request id
     * @param batchId The batch id
     * @param operationId The operation to invoke on the server
     */
    public ManagementRequestHeader(final int version, final  int requestId, final int batchId, final byte operationId) {
        super(version);
        this.requestId = requestId;
        this.batchId = batchId;
        this.operationId = operationId;
    }

    ManagementRequestHeader(final int version, final DataInput input) throws IOException {
        super(version);
        read(input);
    }

    /** {@inheritDoc} */
    public void read(final DataInput input) throws IOException {
        ProtocolUtils.expectHeader(input, ManagementProtocol.REQUEST_ID);
        requestId = input.readInt();
        ProtocolUtils.expectHeader(input, ManagementProtocol.BATCH_ID);
        batchId = input.readInt();
        ProtocolUtils.expectHeader(input, ManagementProtocol.OPERATION_ID);
        operationId = input.readByte();
        ProtocolUtils.expectHeader(input, ManagementProtocol.ONE_WAY);
        oneWay = input.readBoolean();
        ProtocolUtils.expectHeader(input, ManagementProtocol.REQUEST_BODY);
    }

    /** {@inheritDoc} */
    public void write(final DataOutput output) throws IOException {
        super.write(output);
        output.write(ManagementProtocol.REQUEST_ID);
        output.writeInt(requestId);
        output.write(ManagementProtocol.BATCH_ID);
        output.writeInt(batchId);
        output.write(ManagementProtocol.OPERATION_ID);
        output.write(operationId);
        output.write(ManagementProtocol.ONE_WAY);
        output.writeBoolean(oneWay);
        output.write(ManagementProtocol.REQUEST_BODY);
    }

    /**
     * The ID of this request.
     *
     * @return The request id
     */
    public int getRequestId() {
        return requestId;
    }

    /**
     * The ID of the batch this request belongs to
     *
     * @return the batch id
     */
    public int getBatchId() {
        return batchId;
    }

    /**
     * The id of the operation to be executed by this request
     *
     * @return the operation id;
     */
    public byte getOperationId() {
        return operationId;
    }

    @Override
    public byte getType() {
        return ManagementProtocol.TYPE_REQUEST;
    }
}
