/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Parameter validator that requires its resolved value be a member of a given set.
 * @author Paul Ferraro
 * @param <T> the resolved value type
 */
public class SetValidator<T> extends ModelTypeValidator implements AllowedValuesValidator {
    private final Function<ModelNode, T> resolver;
    private final Function<T, ModelNode> formatter;
    private final Set<T> allowed;

    /**
     * Creates a string-based parameter validator the requires the resolved value to be a member of the specified set.
     * @param resolver a model value resolver
     * @param allowed the set of allowed values
     */
    public SetValidator(Function<String, T> resolver, Set<T> allowed) {
        this(ModelType.STRING, resolver.compose(ModelNode::asString), UnaryOperator.<T>identity().andThen(Object::toString).andThen(ModelNode::new), allowed);
    }

    /**
     * Creates a generic parameter validator the requires the resolved value to be a member of the specified set.
     * @param type the model type
     * @param resolver a model value resolver
     * @param formatter a resolved value formatter
     * @param allowed the set of allowed values
     */
    public SetValidator(ModelType type, Function<ModelNode, T> resolver, Function<T, ModelNode> formatter, Set<T> allowed) {
        super(type);
        this.resolver = resolver;
        this.formatter = formatter;
        this.allowed = Set.copyOf(allowed);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);
        if (value.isDefined() && (value.getType() != ModelType.EXPRESSION)) {
            try {
                T resolved = this.resolver.apply(value);
                if (!this.allowed.contains(resolved)) {
                    throw new OperationFailedException(ControllerLogger.MGMT_OP_LOGGER.invalidValue(value.asString(), parameterName, this.allowed.stream().map(this.formatter).map(ModelNode::asString).toList()));
                }
            } catch (RuntimeException e) {
                // Resolution failed
                throw new OperationFailedException(e);
            }
        }
    }

    @Override
    public List<ModelNode> getAllowedValues() {
        return this.allowed.stream().map(this.formatter).toList();
    }
}
