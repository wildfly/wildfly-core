/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import java.util.List;

import javax.xml.stream.XMLStreamConstants;

import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface ModelTestParser extends  XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<ModelMarshallingContext> {

}
