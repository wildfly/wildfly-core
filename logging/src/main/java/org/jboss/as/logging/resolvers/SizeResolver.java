/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.resolvers;

import static org.jboss.as.logging.Logging.createOperationFailure;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;

/**
 * Date: 15.12.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SizeResolver implements ModelNodeResolver<String> {

    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)([kKmMgGbBtT])?");

    public static final SizeResolver INSTANCE = new SizeResolver();

    @Override
    public String resolveValue(final OperationContext context, final ModelNode value) throws OperationFailedException {
        return String.valueOf(parseSize(value));
    }

    public long parseSize(final ModelNode value) throws OperationFailedException {
        final Matcher matcher = SIZE_PATTERN.matcher(value.asString());
        if (!matcher.matches()) {
            throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidSize(value.asString()));
        }
        long qty = Long.parseLong(matcher.group(1), 10);
        final String chr = matcher.group(2);
        if (chr != null) {
            switch (chr.charAt(0)) {
                case 'b':
                case 'B':
                    break;
                case 'k':
                case 'K':
                    qty <<= 10L;
                    break;
                case 'm':
                case 'M':
                    qty <<= 20L;
                    break;
                case 'g':
                case 'G':
                    qty <<= 30L;
                    break;
                case 't':
                case 'T':
                    qty <<= 40L;
                    break;
                default:
                    throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidSize(value.asString()));
            }
        }
        return qty;

    }
}
