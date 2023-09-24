/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

/**
 * A combination of {@code ResourceTransformer} and {@code OperationTransformer}.
 *
 * @author Emanuel Muckenhuber
 */
public interface CombinedTransformer extends ResourceTransformer, OperationTransformer {

}
