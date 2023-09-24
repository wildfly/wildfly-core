/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.transform.description;

import java.util.regex.Pattern;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ExpressionPattern {
    static final Pattern EXPRESSION_PATTERN = Pattern.compile(".*\\$\\{.*\\}.*");
}
