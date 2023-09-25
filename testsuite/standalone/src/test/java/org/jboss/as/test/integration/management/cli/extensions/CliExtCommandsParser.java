/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli.extensions;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.util.List;

/**
 *
 * @author Alexey Loubyansky
 */
public class CliExtCommandsParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

    public static final String NAMESPACE = "urn:jboss:domain:test-cli-ext-commands:1.0";

    @Override
    public void readElement(XMLExtendedStreamReader reader,
            List<ModelNode> ops) throws XMLStreamException {
        ParseUtils.requireNoAttributes(reader);
        ParseUtils.requireNoContent(reader);
        ops.add(Util.createAddOperation(PathAddress.pathAddress(CliExtCommandsSubsystemResourceDescription.PATH)));
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter streamWriter,
            SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(NAMESPACE, true);
    }
}
