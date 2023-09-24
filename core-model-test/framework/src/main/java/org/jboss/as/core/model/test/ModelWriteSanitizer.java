/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test;

import org.jboss.dmr.ModelNode;

/**
 * Allows you to remove parts of the model before persisting/marshalling to xml.
 *
 * @see ModelInitializer
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface ModelWriteSanitizer {
    ModelNode sanitize(ModelNode model);
}
