/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@FunctionalInterface
public interface ModelFixer {
    ModelNode fixModel(ModelNode modelNode);

    public static class CumulativeModelFixer implements ModelFixer {
        ModelFixer[] fixers;

        public CumulativeModelFixer(ModelFixer...fixers) {
            this.fixers = fixers;
        }

        @Override
        public ModelNode fixModel(ModelNode modelNode) {
            for (ModelFixer fixer : fixers) {
                if (fixer != null) {
                    modelNode = fixer.fixModel(modelNode);
                }
            }
            return modelNode;
        }
    }
}
