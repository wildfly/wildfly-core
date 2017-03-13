/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.DelegatingResource;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;

/**
 * A {@link Resource} to represent a {@link KeyStoreDefinition}, the majority is actually model but child resources are a
 * runtime concern.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class KeyStoreResource extends DelegatingResource {

    private ServiceController<KeyStore> keyStoreServiceController;

    KeyStoreResource(Resource delegate) {
        super(delegate);
    }

    /**
     * Set the {@link ServiceController<KeyStore>} for the {@link KeyStore} represented by this {@link Resource}.
     *
     * @param keyStoreServiceController The {@link ServiceController<KeyStore>} to obtain the {@link KeyStore} from.
     */
    public void setKeyStoreServiceController(ServiceController<KeyStore> keyStoreServiceController) {
        this.keyStoreServiceController = keyStoreServiceController;
    }

    @Override
    public Set<String> getChildTypes() {
        if (containsAliases()) {
            return Collections.singleton(ElytronDescriptionConstants.ALIAS);
        }
        return Collections.emptySet();
    }

    @Override
    public boolean hasChildren(String childType) {
        return ElytronDescriptionConstants.ALIAS.equals(childType) && containsAliases();
    }

    @Override
    public boolean hasChild(PathElement element) {
        final KeyStore keyStore;
        try {
            return (ElytronDescriptionConstants.ALIAS.equals(element.getKey()) && (keyStore = getKeyStore(keyStoreServiceController)) != null && keyStore.containsAlias(element.getValue()));
        } catch (KeyStoreException | IllegalStateException e) {
            ElytronSubsystemMessages.ROOT_LOGGER.trace(e);
            return false;
        }
    }

    @Override
    public Resource getChild(PathElement element) {
        final KeyStore keyStore;
        try {
            if (ElytronDescriptionConstants.ALIAS.equals(element.getKey()) && (keyStore = getKeyStore(keyStoreServiceController)) != null && keyStore.containsAlias(element.getValue())) {
                return PlaceholderResource.INSTANCE;
            }
        } catch (KeyStoreException | IllegalStateException e) {
            ElytronSubsystemMessages.ROOT_LOGGER.trace(e);
        }

        return null;
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
        final KeyStore keyStore;
        try {
            if (ElytronDescriptionConstants.ALIAS.equals(childType) && (keyStore = getKeyStore(keyStoreServiceController)) != null && keyStore.size() > 0) {
                Enumeration<String> aliases = keyStore.aliases();
                Set<String> children = new LinkedHashSet<String>(keyStore.size());
                while (aliases.hasMoreElements()) {
                    children.add(aliases.nextElement());
                }

                return children;
            }
        } catch (KeyStoreException | IllegalStateException e) {
            ElytronSubsystemMessages.ROOT_LOGGER.trace(e);
        }

        return Collections.emptySet();
    }

    @Override
    public Set<ResourceEntry> getChildren(String childType) {
        final KeyStore keyStore;
        try {
            if (ElytronDescriptionConstants.ALIAS.equals(childType) && (keyStore = getKeyStore(keyStoreServiceController)) != null && keyStore.size() > 0) {
                Enumeration<String> aliases = keyStore.aliases();
                Set<ResourceEntry> children = new LinkedHashSet<ResourceEntry>(keyStore.size());
                while (aliases.hasMoreElements()) {
                    children.add(new PlaceholderResource.PlaceholderResourceEntry(ElytronDescriptionConstants.ALIAS, aliases.nextElement()));
                }

                return children;
            }
        } catch (KeyStoreException | IllegalStateException e) {
            ElytronSubsystemMessages.ROOT_LOGGER.trace(e);
        }

        return Collections.emptySet();
    }

    @Override
    public Resource navigate(PathAddress address) {
        return Resource.Tools.navigate(this, address);
    }

    @Override
    public Resource clone() {
        KeyStoreResource keyStoreResource = new KeyStoreResource(super.clone());
        keyStoreResource.setKeyStoreServiceController(keyStoreServiceController);
        return keyStoreResource;
    }

    /**
     * Check if the {@link KeyStore} contains any aliases.
     *
     * @return {@code true} if the {@link KeyStore} is available and contains at least one entry, {@code false} otherwise.
     */
    private boolean containsAliases() {
        final KeyStore keyStore;

        try {
            return ((keyStore = getKeyStore(keyStoreServiceController)) != null) && keyStore.size() > 0;
        } catch (KeyStoreException | IllegalStateException e) {
            ElytronSubsystemMessages.ROOT_LOGGER.trace(e);
            return false;
        }
    }

    /**
     * Get the {@link KeyStore} represented by this {@link Resource} or {@code null} if it is not currently available.
     *
     * @return The {@link KeyStore} represented by this {@link Resource} or {@code null} if it is not currently available.
     */
    static KeyStore getKeyStore(ServiceController<KeyStore> keyStoreServiceController) {
        if (keyStoreServiceController == null || keyStoreServiceController.getState() != State.UP) {
            return null;
        } else {
            return keyStoreServiceController.getValue();
        }
    }

}
