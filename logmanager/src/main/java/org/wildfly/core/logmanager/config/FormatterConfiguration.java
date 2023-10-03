/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import java.util.logging.Formatter;

/**
 * A configuration for a logger formatter.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface FormatterConfiguration extends NamedConfigurable, ObjectConfigurable<Formatter>, PropertyConfigurable {
}
