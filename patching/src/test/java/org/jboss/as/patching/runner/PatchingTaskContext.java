/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

/**
 * @author Emanuel Muckenhuber
 */
public interface PatchingTaskContext {

    enum Mode {

        APPLY,
        UNDO,
        ROLLBACK,
        ;

    }

}
