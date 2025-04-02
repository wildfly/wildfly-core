/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

/**
 * Adds absentee attribute handling to an {link org.jboss.staxmapper.XMLAttributeReader}.
 * @author Paul Ferraro
 * @param <C> the reader context type
 */
public interface XMLAttributeReader<C> extends org.jboss.staxmapper.XMLAttributeReader<C>, XMLComponentReader<C> {

}
