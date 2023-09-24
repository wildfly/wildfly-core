/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import java.util.List;

/**
 *
 * @author Alexey Loubyansky
 */
public interface Operation extends Operand {

    String getName();

    int getPriority();

    List<Operand> getOperands();
}
