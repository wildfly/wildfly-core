/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management.audit.validators;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Date: 07.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SizeValidator extends ModelTypeValidator {

    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)([kKmMgGbBtT])?");

    public SizeValidator() {
        this(false);
    }

    public SizeValidator(final boolean nullable) {
        super(ModelType.STRING, nullable);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            parseSize(value);
        }
    }

    public static long parseSize(final ModelNode value) throws OperationFailedException {
        final Matcher matcher = SIZE_PATTERN.matcher(value.asString());
        if (!matcher.matches()) {
            throw DomainManagementLogger.ROOT_LOGGER.invalidSize(value.asString());
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
                    throw DomainManagementLogger.ROOT_LOGGER.invalidSize(value.asString());
            }
        }
        return qty;
    }

}
