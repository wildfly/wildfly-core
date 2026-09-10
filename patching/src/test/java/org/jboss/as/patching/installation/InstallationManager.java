/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.installation;

/**
 * The installation manager, basically represents a mutable {@code InstalledIdentity}.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class InstallationManager {

    public abstract InstalledIdentity getDefaultIdentity();

}
