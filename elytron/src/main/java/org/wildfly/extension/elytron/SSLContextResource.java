/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.DelegatingResource;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.wildfly.common.iteration.ByteIterator;
import org.wildfly.security.dynamic.ssl.DynamicSSLContext;

/**
 * A {@link Resource} to represent a server-ssl-context/client-ssl-context, the majority is actually model
 * but child resources are a runtime concern.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SSLContextResource extends DelegatingResource {

    private ServiceController<SSLContext> sslContextServiceController;
    private boolean server;

    SSLContextResource(Resource delegate, boolean server) {
        super(delegate);
        this.server = server;
    }

    /**
     * Set the {@link ServiceController<SSLContext>} for the {@link SSLContext} represented by this {@link Resource}.
     *
     * @param sslContextServiceController The {@link ServiceController<SSLContext>} to obtain the {@link SSLContext} from.
     */
    void setSSLContextServiceController(ServiceController<SSLContext> sslContextServiceController) {
        this.sslContextServiceController = sslContextServiceController;
    }

    @Override
    public Set<String> getChildTypes() {
        if (hasActiveSessions()) {
            return Collections.singleton(ElytronDescriptionConstants.SSL_SESSION);
        }
        return Collections.emptySet();
    }

    @Override
    public boolean hasChildren(String childType) {
        return ElytronDescriptionConstants.SSL_SESSION.equals(childType) && hasActiveSessions();
    }

    @Override
    public boolean hasChild(PathElement element) {
        SSLContext sslContext;
        if (ElytronDescriptionConstants.SSL_SESSION.equals(element.getKey()) && (sslContext = getSSLContext(sslContextServiceController)) != null) {
            byte[] sessionId = ByteIterator.ofBytes(element.getValue().getBytes(StandardCharsets.UTF_8)).asUtf8String().hexDecode().drain();
            SSLSessionContext sslSessionContext = server ? sslContext.getServerSessionContext() : sslContext.getClientSessionContext();
            return sslSessionContext.getSession(sessionId) != null;
        }
        return false;
    }

    @Override
    public Resource getChild(PathElement element) {
        return hasChild(element) ? PlaceholderResource.INSTANCE : null;
    }

    @Override
    public Resource requireChild(PathElement element) {
        Resource resource = getChild(element);
        if (resource == null) {
            throw new NoSuchResourceException(element);
        }
        return resource;
    }

    @Override
    public Set<String> getChildrenNames(String childType) {
        SSLContext sslContext;
        if (ElytronDescriptionConstants.SSL_SESSION.equals(childType) && (sslContext = getSSLContext(sslContextServiceController)) != null) {
            SSLSessionContext sslSessionContext = server ? sslContext.getServerSessionContext() : sslContext.getClientSessionContext();
            Set<String> set = new HashSet<>();
            for (byte[] b : Collections.list(sslSessionContext.getIds())) {
                String s = ByteIterator.ofBytes(b).hexEncode(true).drainToString();
                set.add(s);
            }
            return set;
        }
        return Collections.emptySet();
    }

    @Override
    public Set<ResourceEntry> getChildren(String childType) {
        Set<ResourceEntry> set = new HashSet<>();
        for (String s : getChildrenNames(childType)) {
            PlaceholderResource.PlaceholderResourceEntry placeholderResourceEntry = new PlaceholderResource.PlaceholderResourceEntry(ElytronDescriptionConstants.SSL_SESSION, s);
            set.add(placeholderResourceEntry);
        }
        return set;
    }

    @Override
    public Resource navigate(PathAddress address) {
        return Resource.Tools.navigate(this, address);
    }

    @Override
    public Resource clone() {
        SSLContextResource sslContextResource = new SSLContextResource(super.clone(), server);
        sslContextResource.setSSLContextServiceController(sslContextServiceController);
        return sslContextResource;
    }

    /**
     * Check if the {@link SSLContext} has any active sessions.
     *
     * @return {@code true} if the {@link SSLContext} is available and has at least one session, {@code false} otherwise.
     */
    private boolean hasActiveSessions() {
        final SSLContext sslContext = getSSLContext(sslContextServiceController);
        if (sslContext instanceof DynamicSSLContext) {
            return false;
        }
        if (sslContext == null) return false;
        SSLSessionContext sslSessionContext = server ? sslContext.getServerSessionContext() : sslContext.getClientSessionContext();
        return sslSessionContext.getIds().hasMoreElements();
    }

    /**
     * Get the {@link SSLContext} represented by this {@link Resource} or {@code null} if it is not currently available.
     *
     * @return The {@link SSLContext} represented by this {@link Resource} or {@code null} if it is not currently available.
     */
    static SSLContext getSSLContext(ServiceController<SSLContext> sslContextServiceController) {
        if (sslContextServiceController == null || sslContextServiceController.getState() != State.UP) {
            return null;
        } else {
            return sslContextServiceController.getValue();
        }
    }

}
