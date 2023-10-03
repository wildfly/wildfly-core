/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import java.util.logging.Filter;

/**
 * A configuration for a filter.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface FilterConfiguration extends ObjectConfigurable<Filter>, NamedConfigurable, PropertyConfigurable {
}
