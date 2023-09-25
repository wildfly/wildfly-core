/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.interfaces;

import java.io.IOException;

import org.jboss.dmr.ModelNode;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public interface ManagementInterface {
    ModelNode execute(ModelNode operation);
    void close() throws IOException;
}
