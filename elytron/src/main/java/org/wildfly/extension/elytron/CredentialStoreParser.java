/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CREDENTIAL_STORES;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder;

/**
 * A parser for Credential Store definition.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class CredentialStoreParser {

    final PersistentResourceXMLDescription credentialStoreParser = builder(PathElement.pathElement(ElytronDescriptionConstants.CREDENTIAL_STORE))
            .setUseElementsForGroups(false)
            .addAttribute(CredentialStoreResourceDefinition.TYPE)
            .addAttribute(CredentialStoreResourceDefinition.PROVIDER_NAME)
            .addAttribute(CredentialStoreResourceDefinition.PROVIDERS)
            .addAttribute(CredentialStoreResourceDefinition.OTHER_PROVIDERS)
            .addAttribute(CredentialStoreResourceDefinition.RELATIVE_TO)
            .addAttribute(CredentialStoreResourceDefinition.LOCATION)
            .addAttribute(CredentialStoreResourceDefinition.MODIFIABLE)
            .addAttribute(CredentialStoreResourceDefinition.CREATE)
            .addAttribute(CredentialStoreResourceDefinition.IMPLEMENTATION_PROPERTIES)
            .addAttribute(CredentialStoreResourceDefinition.CREDENTIAL_REFERENCE)
            .build();

    final PersistentResourceXMLDescription credentialStoreParser_13 = builder(PathElement.pathElement(ElytronDescriptionConstants.CREDENTIAL_STORE))
            .setUseElementsForGroups(false)
            .addAttribute(CredentialStoreResourceDefinition.TYPE)
            .addAttribute(CredentialStoreResourceDefinition.PROVIDER_NAME)
            .addAttribute(CredentialStoreResourceDefinition.PROVIDERS)
            .addAttribute(CredentialStoreResourceDefinition.OTHER_PROVIDERS)
            .addAttribute(CredentialStoreResourceDefinition.RELATIVE_TO)
            .addAttribute(CredentialStoreResourceDefinition.LOCATION)
            .addAttribute(CredentialStoreResourceDefinition.PATH)
            .addAttribute(CredentialStoreResourceDefinition.MODIFIABLE)
            .addAttribute(CredentialStoreResourceDefinition.CREATE)
            .addAttribute(CredentialStoreResourceDefinition.IMPLEMENTATION_PROPERTIES)
            .addAttribute(CredentialStoreResourceDefinition.CREDENTIAL_REFERENCE)
            .build();

    final PersistentResourceXMLDescription secretKeyCredentialStoreParser = builder(PathElement.pathElement(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE))
            .setUseElementsForGroups(false)
            .addAttributes(SecretKeyCredentialStoreDefinition.CONFIG_ATTRIBUTES)
            .build();

    PersistentResourceXMLBuilder getCredentialStoresParser() {
        return decorator(CREDENTIAL_STORES).addChild(new CredentialStoreParser().credentialStoreParser);
    }

    PersistentResourceXMLBuilder getCredentialStoresParser_13() {
        return decorator(CREDENTIAL_STORES).addChild(new CredentialStoreParser().credentialStoreParser_13)
                .addChild(secretKeyCredentialStoreParser);
    }

    CredentialStoreParser() {
    }

}
