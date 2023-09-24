/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import static org.jboss.as.protocol.mgmt.ProtocolUtils.expectHeader;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

import org.jboss.as.protocol.logging.ProtocolLogger;

/**
 * ManagementProtocol header used to send the required information to establish a request with a remote controller.  The primary
 * pieces of the request are the protocol signature and the protocol version being used.
 *
 * @author John Bailey
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class ManagementProtocolHeader {

    private int version;

    /**
     * Construct an instance with the protocol version for the header.
     *
     * @param version The protocol version
     */
    protected ManagementProtocolHeader(int version) {
        this.version = version;
    }
    /**
     * Write the header information to the provided {@link java.io.DataOutput}.
     *
     * @param output The output to write to
     * @throws IOException If any problems occur writing to the output
     */
    public void write(final DataOutput output) throws IOException {
        output.write(ManagementProtocol.SIGNATURE);
        output.writeByte(ManagementProtocol.VERSION_FIELD);
        output.writeInt(getVersion());
        output.writeByte(ManagementProtocol.TYPE);
        output.writeByte(getType());
    }

    /**
     * The protocol version for the current communication.
     *
     * @return The protocol version
     */
    public int getVersion() {
        return version;
    }

    /**
     * The type
     *
     * @return the protocol byte identifying the type
     */
    public abstract byte getType();


    /**
     * Validate the header signature.
     *
     * @param input The input to read the signature from
     * @throws IOException If any read problems occur
     */
    protected static void validateSignature(final DataInput input) throws IOException {
        final byte[] signatureBytes = new byte[4];
        input.readFully(signatureBytes);
        if (!Arrays.equals(ManagementProtocol.SIGNATURE, signatureBytes)) {
            throw ProtocolLogger.ROOT_LOGGER.invalidSignature(Arrays.toString(signatureBytes));
        }
    }

    protected <T extends ManagementProtocolHeader> T cast(Class<T> expected) {
        return expected.cast(this);
    }

    /**
     * Parses the input stream to read the header
     *
     * @param input data input to read from
     * @return the parsed protocol header
     * @throws IOException
     */
    public static ManagementProtocolHeader parse(DataInput input) throws IOException {
        validateSignature(input);
        expectHeader(input, ManagementProtocol.VERSION_FIELD);
        int version = input.readInt();
        expectHeader(input, ManagementProtocol.TYPE);
        byte type = input.readByte();
        switch (type) {
            case ManagementProtocol.TYPE_REQUEST:
                return new ManagementRequestHeader(version, input);
            case ManagementProtocol.TYPE_RESPONSE:
                return new ManagementResponseHeader(version, input);
            case ManagementProtocol.TYPE_BYE_BYE:
                return new ManagementByeByeHeader(version);
            case ManagementProtocol.TYPE_PING:
                return new ManagementPingHeader(version);
            case ManagementProtocol.TYPE_PONG:
                return new ManagementPongHeader(version);
            default:
                throw ProtocolLogger.ROOT_LOGGER.invalidType("0x" + Integer.toHexString(type));
        }
    }
}
