/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.operations.validation;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates whether the provided string represents a valid character set.
 *
 * @author <a href="mailto:szaldana@redhat.com">Sonia Zaldana</a>
 */
public class CharsetValidator extends ModelTypeValidator {

    public CharsetValidator() {
        super(ModelType.STRING);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined()) {
            try {
                Charset hashCharset = Charset.forName(value.asString());
            } catch (IllegalCharsetNameException e) {
                throw ControllerLogger.ROOT_LOGGER.illegalCharsetName(value.asString());
            } catch (UnsupportedCharsetException e) {
                throw ControllerLogger.ROOT_LOGGER.unsupportedCharset(value.asString());
            }
        }
    }
}
