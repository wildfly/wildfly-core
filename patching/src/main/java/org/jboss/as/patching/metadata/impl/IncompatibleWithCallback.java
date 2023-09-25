/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata.impl;

/**
 * @author Emanuel Muckenhuber
 */
public interface IncompatibleWithCallback {

    IncompatibleWithCallback incompatibleWith(String patchID);

}
