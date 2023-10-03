/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import java.util.logging.ErrorManager;

/**
 * Configuration for an error manager.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ErrorManagerConfiguration extends ObjectConfigurable<ErrorManager>, PropertyConfigurable, NamedConfigurable {
}
