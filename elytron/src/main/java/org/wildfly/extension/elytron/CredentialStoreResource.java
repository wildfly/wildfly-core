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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.DelegatingResource;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.msc.service.ServiceController;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;

/**
 * A {@link Resource} to represent a {@link CredentialStoreResourceDefinition}.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 */
class CredentialStoreResource extends DelegatingResource {

    private ServiceController<CredentialStore> credentialStoreServiceController;

    CredentialStoreResource(Resource delegate) {
        super(delegate);
    }

    public void setCredentialStoreServiceController(ServiceController<CredentialStore> credentialStoreServiceController) {
        this.credentialStoreServiceController = credentialStoreServiceController;
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
        if (ElytronDescriptionConstants.ALIAS.equals(element.getKey())) {
            try {
                CredentialStore credentialStore = credentialStoreServiceController != null ? credentialStoreServiceController.getValue() : null;
                if (credentialStore != null && (credentialStore.getAliases().contains(toLower(element.getValue())))) {
                    return true;
                }
            } catch (CredentialStoreException e) {
                ElytronSubsystemMessages.ROOT_LOGGER.credentialStoreIssueEncountered(e);
            }
            return false;
        }
        return super.hasChild(element);
    }

    @Override
    public Resource getChild(PathElement element) {
        if (ElytronDescriptionConstants.ALIAS.equals(element.getKey())) {
            try {
                CredentialStore credentialStore = credentialStoreServiceController != null ? credentialStoreServiceController.getValue() : null;
                if (credentialStore != null && (credentialStore.getAliases().contains(toLower(element.getValue())))) {
                    return Resource.Factory.create(true);
                }
            } catch (CredentialStoreException e) {
                ElytronSubsystemMessages.ROOT_LOGGER.credentialStoreIssueEncountered(e);
            }
            return null;
        }
        return super.getChild(element);
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
        if (ElytronDescriptionConstants.ALIAS.equals(childType)) {
            try {
                CredentialStore credentialStore = credentialStoreServiceController != null ? credentialStoreServiceController.getValue() : null;
                if (credentialStore != null && credentialStore.isInitialized()) {
                    return credentialStore.getAliases();
                }
            } catch (CredentialStoreException e) {
                ElytronSubsystemMessages.ROOT_LOGGER.credentialStoreIssueEncountered(e);
            }
            return Collections.emptySet();
        }
        return super.getChildrenNames(childType);
    }

    @Override
    public Set<ResourceEntry> getChildren(String childType) {
        if (ElytronDescriptionConstants.ALIAS.equals(childType)) {
            try {
                CredentialStore credentialStore = credentialStoreServiceController != null ? credentialStoreServiceController.getValue() : null;
                if (credentialStore != null && credentialStore.isInitialized() && credentialStore.getAliases().size() > 0) {
                    Set<String> aliases = credentialStore.getAliases();
                    Set<ResourceEntry> children = new LinkedHashSet<>(aliases.size());
                    children.addAll(aliases.stream().map(alias -> new PlaceholderResource.PlaceholderResourceEntry(ElytronDescriptionConstants.ALIAS, alias)).collect(Collectors.toList()));
                    return children;
                }
            } catch (CredentialStoreException e) {
                ElytronSubsystemMessages.ROOT_LOGGER.credentialStoreIssueEncountered(e);
            }
            return Collections.emptySet();
        }
        return super.getChildren(childType);
    }

    @Override
    public Resource navigate(PathAddress address) {
        return Resource.Tools.navigate(this, address);
    }

    @Override
    public Resource clone() {
        CredentialStoreResource credentialStoreResource = new CredentialStoreResource(super.clone());
        credentialStoreResource.setCredentialStoreServiceController(credentialStoreServiceController);
        return credentialStoreResource;
    }

    private boolean containsAliases() {
        try {
            CredentialStore credentialStore = credentialStoreServiceController != null ? credentialStoreServiceController.getValue() : null;
            return credentialStore != null && credentialStore.isInitialized() && credentialStore.getAliases().size() > 0;
        } catch (CredentialStoreException e) {
            return false;
        }
    }

    private String toLower(String parameter) {
        return parameter != null ? parameter.toLowerCase(Locale.ROOT) : null;
    }

    @Override
    public Resource removeChild(PathElement element) {
        if (!ElytronDescriptionConstants.ALIAS.equals(element.getKey())) {
            return super.removeChild(element);
        }
        return null;
    }

    @Override
    public void registerChild(PathElement element, int index, Resource resource) {
        if (!ElytronDescriptionConstants.ALIAS.equals(element.getKey())) {
            super.registerChild(element, index, resource);
        } else if (getChild(element) != null) {
            throw ControllerLogger.ROOT_LOGGER.duplicateResource(element.getValue());
        }
    }

    @Override
    public void registerChild(PathElement element, Resource resource) {
        if (!ElytronDescriptionConstants.ALIAS.equals(element.getKey())) {
            super.registerChild(element, resource);
        } else if (getChild(element) != null) {
            throw ControllerLogger.ROOT_LOGGER.duplicateResource(element.getValue());
        }
    }

}
