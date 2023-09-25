/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import org.jboss.dmr.ModelNode;

/**
 * Represents objects which are convertable to ModelNode instances.
 *
 * @author Josef Cacek
 */
public interface ModelNodeConvertable {

    ModelNode toModelNode();

}
