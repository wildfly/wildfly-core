/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.operation.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * This completer completes values of a parameter based on its description.
 *
 * @author Alexey Loubyansky
 */
public class AttributeTypeDescrValueCompleter extends DefaultCompleter {

    private static final List<String> BOOLEAN = Arrays.asList(new String[]{"false", "true"});

    public AttributeTypeDescrValueCompleter(final ModelNode attrDescr) {
        super(new CandidatesProvider(){

            @Override
            public Collection<String> getAllCandidates(CommandContext ctx) {

                final ModelNode typeNode = attrDescr.get(Util.TYPE);
                if(typeNode.isDefined() && typeNode.asType().equals(ModelType.BOOLEAN)) {
                    return BOOLEAN;
                } else if(attrDescr.has(Util.ALLOWED)) {
                    final ModelNode allowedNode = attrDescr.get(Util.ALLOWED);
                    if(allowedNode.isDefined()) {
                        final List<ModelNode> nodeList = allowedNode.asList();
                        final List<String> values = new ArrayList<String>(nodeList.size());
                        for(ModelNode node : nodeList) {
                            values.add(node.asString());
                        }
                        return values;
                    }
                }
                return Collections.emptyList();
            }});
    }
}
