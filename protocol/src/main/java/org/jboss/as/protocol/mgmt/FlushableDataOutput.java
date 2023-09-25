/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.protocol.mgmt;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.Flushable;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface FlushableDataOutput extends DataOutput, Flushable, Closeable {

}
