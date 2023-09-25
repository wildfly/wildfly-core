/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.security.KeyStore;

import org.jboss.msc.service.Service;

/**
 * An interface for KeyStore services, which provide modifiable KeyStore.
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
interface ModifiableKeyStoreService extends Service<KeyStore> {

    KeyStore getModifiableValue();

}
