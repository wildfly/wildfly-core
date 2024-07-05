/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import java.util.List;

import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.xml.IntVersionSchema;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;

/**
 * Base representation of a schema for the management model.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface ManagementXmlSchema extends IntVersionSchema<ManagementXmlSchema>, XMLElementReader<List<ModelNode>>,
                                        XMLElementWriter<ModelMarshallingContext>  {
}
