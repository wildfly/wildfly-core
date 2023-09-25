/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.wildfly.extension.elytron.CertificateChainAttributeDefinitions.getNamedCertificateList;
import static org.wildfly.extension.elytron.CertificateChainAttributeDefinitions.writeCertificates;
import static org.wildfly.extension.elytron.ElytronExtension.ISO_8601_FORMAT;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.CertificateEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.common.iteration.ByteIterator;

/**
 * A resource definition to represent a currently established SSL session.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SSLSessionDefinition extends SimpleResourceDefinition {

    private static final SimpleAttributeDefinition APPLICATION_BUFFER_SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.APPLICATION_BUFFER_SIZE, ModelType.INT)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition CIPHER_SUITE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CIPHER_SUITE, ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition CREATION_TIME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CREATION_TIME, ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition LAST_ACCESSED_TIME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LAST_ACCESSED_TIME, ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition LOCAL_PRINCIPAL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LOCAL_PRINCIPAL, ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition PACKET_BUFFER_SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PACKET_BUFFER_SIZE, ModelType.INT)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition PEER_HOST = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PEER_HOST, ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition PEER_PORT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PEER_PORT, ModelType.INT)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition PEER_PRINCIPAL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PEER_PRINCIPAL, ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROTOCOL, ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition VALID = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALID, ModelType.BOOLEAN)
            .setStorageRuntime()
            .build();

    private static final ResourceDescriptionResolver RESOURCE_DESCRIPTION_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, ElytronDescriptionConstants.SSL_SESSION);


    private static final SimpleOperationDefinition INVALIDATE = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.INVALIDATE, RESOURCE_DESCRIPTION_RESOLVER)
            .build();

    private boolean server;

    SSLSessionDefinition(boolean server) {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.SSL_SESSION), RESOURCE_DESCRIPTION_RESOLVER)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRuntime());
        this.server = server;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(APPLICATION_BUFFER_SIZE, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> r.set(s.getApplicationBufferSize())));
        resourceRegistration.registerReadOnlyAttribute(CIPHER_SUITE, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> r.set(s.getCipherSuite())));
        resourceRegistration.registerReadOnlyAttribute(CREATION_TIME, new SSLSessionRuntimeHandler(
                (ModelNode r, SSLSession s) -> r.set(new SimpleDateFormat(ISO_8601_FORMAT).format(new Date(s.getCreationTime())))));
        resourceRegistration.registerReadOnlyAttribute(LAST_ACCESSED_TIME, new SSLSessionRuntimeHandler(
                (ModelNode r, SSLSession s) -> r.set(new SimpleDateFormat(ISO_8601_FORMAT).format(new Date(s.getLastAccessedTime())))));
        resourceRegistration.registerReadOnlyAttribute(getNamedCertificateList(ElytronDescriptionConstants.LOCAL_CERTIFICATES),
                new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> {
                    try {
                        writeCertificates(r, s.getLocalCertificates());
                    } catch (CertificateEncodingException | NoSuchAlgorithmException ignored) {
                    }
                }));
        resourceRegistration.registerReadOnlyAttribute(LOCAL_PRINCIPAL, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> {
                    Principal p = s.getLocalPrincipal();
                    if (p != null) {
                        r.set(p.toString());
                    }
        }));
        resourceRegistration.registerReadOnlyAttribute(PACKET_BUFFER_SIZE, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> r.set(s.getPacketBufferSize())));
        resourceRegistration.registerReadOnlyAttribute(getNamedCertificateList(ElytronDescriptionConstants.PEER_CERTIFICATES),
                new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> {
                    try {
                        writeCertificates(r, s.getPeerCertificates());
                    } catch (CertificateEncodingException | NoSuchAlgorithmException | SSLPeerUnverifiedException ignored) {
                    }
                }));
        resourceRegistration.registerReadOnlyAttribute(PEER_HOST, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> r.set(s.getPeerHost())));
        resourceRegistration.registerReadOnlyAttribute(PEER_PORT, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> r.set(s.getPeerPort())));
        resourceRegistration.registerReadOnlyAttribute(PEER_PRINCIPAL, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> {
                    try {
                        Principal p = s.getPeerPrincipal();
                        if (p != null) {
                            r.set(p.toString());
                        }
                    } catch (SSLPeerUnverifiedException ignored) {}
        }));
        resourceRegistration.registerReadOnlyAttribute(PROTOCOL, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> r.set(s.getProtocol())));
        resourceRegistration.registerReadOnlyAttribute(VALID, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> r.set(s.isValid())));
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(INVALIDATE, new SSLSessionRuntimeHandler((ModelNode r, SSLSession s) -> s.invalidate()));
    }

    class SSLSessionRuntimeHandler extends SSLDefinitions.SSLContextRuntimeHandler {

        private final BiConsumer<ModelNode, SSLSession> biConsumer;

        SSLSessionRuntimeHandler(BiConsumer<ModelNode, SSLSession> biConsumer) {
            this.biConsumer = biConsumer;
        }
        @Override
        protected void performRuntime(ModelNode result, ModelNode operation, SSLContext sslContext) throws OperationFailedException {
            SSLSessionContext sslSessionContext = server ? sslContext.getServerSessionContext() : sslContext.getClientSessionContext();
            SSLSession sslSession = sslSessionContext.getSession(sessionId(operation));
            if (sslSession != null) {
                performRuntime(result, operation, sslSession);
            }
        }

        protected void performRuntime(ModelNode result, ModelNode operation, SSLSession sslSession) throws OperationFailedException {
            biConsumer.accept(result, sslSession);
        }

        @Override
        protected ServiceUtil<SSLContext> getSSLContextServiceUtil() {
            return server ? SSLDefinitions.SERVER_SERVICE_UTIL : SSLDefinitions.CLIENT_SERVICE_UTIL;
        }
    }

    private static byte[] sessionId(ModelNode operation) {
        PathAddress pa = PathAddress.pathAddress(operation.require(OP_ADDR));
        for (int i = pa.size() - 1; i > 0; i--) {
            PathElement pe = pa.getElement(i);
            if (ElytronDescriptionConstants.SSL_SESSION.equals(pe.getKey())) {
                return ByteIterator.ofBytes(pe.getValue().getBytes(StandardCharsets.UTF_8)).asUtf8String().hexDecode().drain();
            }
        }

        throw ROOT_LOGGER.operationAddressMissingKey(ElytronDescriptionConstants.SSL_SESSION);
    }
}
