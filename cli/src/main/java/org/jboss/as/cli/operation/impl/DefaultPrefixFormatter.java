/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import java.util.Iterator;

import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.NodePathFormatter;
import org.jboss.as.cli.operation.OperationRequestAddress.Node;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultPrefixFormatter implements NodePathFormatter {

    public static final NodePathFormatter INSTANCE = new DefaultPrefixFormatter();

    /* (non-Javadoc)
     * @see org.jboss.as.cli.PrefixFormatter#format(org.jboss.as.cli.Prefix)
     */
    @Override
    public String format(OperationRequestAddress prefix) {

        Iterator<Node> iterator = prefix.iterator();
        if(!iterator.hasNext()) {
            return "/";
        }

        StringBuilder builder = new StringBuilder();
        builder.append('/');
        Node next = iterator.next();
        builder.append(next.getType());
        if(next.getName() != null) {
            builder.append('=').append(next.getName());
        }
        while(iterator.hasNext()) {
            builder.append('/');
            next = iterator.next();
            builder.append(next.getType());
            if(next.getName() != null) {
                builder.append('=').append(next.getName());
            }
        }
        return builder.toString();
    }

}
