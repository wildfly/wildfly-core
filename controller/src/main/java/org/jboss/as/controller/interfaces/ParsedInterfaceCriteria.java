/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.interfaces;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ANY_ADDRESS;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * Utility class to create an interface criteria based on a {@link ModelNode} description
 *
 * @author Brian Stansberry
 * @author Emanuel Muckenhuber
 */
public final class ParsedInterfaceCriteria {

    private static final ParsedInterfaceCriteria ANY = new ParsedInterfaceCriteria(true);

    private final String failureMessage;
    private final boolean anyLocal;
    private final Set<InterfaceCriteria> criteria = new LinkedHashSet<InterfaceCriteria>();

    private ParsedInterfaceCriteria(final String failureMessage) {
        this.failureMessage = failureMessage;
        this.anyLocal = false;
    }

    private ParsedInterfaceCriteria(final boolean anyLocal) {
        this.failureMessage = null;
        this.anyLocal = anyLocal;
    }

    private ParsedInterfaceCriteria(final Set<InterfaceCriteria> criteria) {
        this.failureMessage = null;
        this.anyLocal = false;
        this.criteria.addAll(criteria);
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public boolean isAnyLocal() {
        return anyLocal;
    }

    public Set<InterfaceCriteria> getCriteria() {
        return criteria;
    }

    public static ParsedInterfaceCriteria parse(final ModelNode model, final boolean specified, final ExpressionResolver expressionResolver) {
        if (model.getType() != ModelType.OBJECT) {
            return new ParsedInterfaceCriteria(ControllerLogger.ROOT_LOGGER.illegalInterfaceCriteria(model.getType(), ModelType.OBJECT));
        }
        // Remove operation params
        final ModelNode subModel = model.clone();
        subModel.remove(ModelDescriptionConstants.OP);
        subModel.remove(ModelDescriptionConstants.OP_ADDR);
        subModel.remove(ModelDescriptionConstants.OPERATION_HEADERS);
        final ParsedInterfaceCriteria parsed;
        if(subModel.hasDefined(ANY_ADDRESS) && subModel.get(ANY_ADDRESS).asBoolean(false)) {
            parsed = ParsedInterfaceCriteria.ANY;
        } else {
            try {
                final List<Property> nodes = subModel.asPropertyList();
                final Set<InterfaceCriteria> criteriaSet = new LinkedHashSet<InterfaceCriteria>();
                for (final Property property : nodes) {
                    final InterfaceCriteria criterion = parseCriteria(property, false, expressionResolver);
                    if (criterion instanceof WildcardInetAddressInterfaceCriteria) {
                        // AS7-1668: stop processing and just return the any binding.
                        if (nodes.size() > 1) {
                            MGMT_OP_LOGGER.wildcardAddressDetected();
                        }
                        return ParsedInterfaceCriteria.ANY;
                    }
                    else if (criterion != null) {
                        criteriaSet.add(criterion);
                    }
                }
                String validation = new CriteriaValidator(criteriaSet).validate();
                parsed = validation == null ? new ParsedInterfaceCriteria(criteriaSet) : new ParsedInterfaceCriteria(validation);
            } catch (ParsingException p) {
                return new ParsedInterfaceCriteria(p.msg);
            } catch (OperationFailedException e) {
                return new ParsedInterfaceCriteria(e.getMessage());
            }
        }
        if (specified && parsed.getFailureMessage() == null && ! parsed.isAnyLocal() && parsed.getCriteria().isEmpty()) {
            return new ParsedInterfaceCriteria(ControllerLogger.ROOT_LOGGER.noInterfaceCriteria());
        }
        return parsed;
    }

    private static InterfaceCriteria parseCriteria(final Property property, final boolean nested,
                                                   final ExpressionResolver expressionResolver) throws OperationFailedException {
        final Element element = Element.forName(property.getName());
        switch (element) {
            case LINK_LOCAL_ADDRESS:
                return LinkLocalInterfaceCriteria.INSTANCE;
            case LOOPBACK:
                return LoopbackInterfaceCriteria.INSTANCE;
            case MULTICAST:
                return SupportsMulticastInterfaceCriteria.INSTANCE;
            case POINT_TO_POINT:
                return PointToPointInterfaceCriteria.INSTANCE;
            case PUBLIC_ADDRESS:
                return PublicAddressInterfaceCriteria.INSTANCE;
            case SITE_LOCAL_ADDRESS:
                return SiteLocalInterfaceCriteria.INSTANCE;
            case UP:
                return UpInterfaceCriteria.INSTANCE;
            case VIRTUAL:
                return VirtualInterfaceCriteria.INSTANCE;
            case INET_ADDRESS: {
                ModelNode value = parsePossibleExpression(property.getValue());
                checkStringType(value, element.getLocalName(), true);
                return createInetAddressCriteria(value, expressionResolver);
            }
            case LOOPBACK_ADDRESS: {
                ModelNode value = parsePossibleExpression(property.getValue());
                checkStringType(value, element.getLocalName(), true);
                return new LoopbackAddressInterfaceCriteria(parseInetAddress(value, expressionResolver));
            }
            case NIC: {
                ModelNode value = parsePossibleExpression(property.getValue());
                checkStringType(property.getValue(), element.getLocalName());
                return new NicInterfaceCriteria(expressionResolver.resolveExpressions(value).asString());
            }
            case NIC_MATCH: {
                ModelNode value = parsePossibleExpression(property.getValue());
                checkStringType(property.getValue(), element.getLocalName());
                return createNicMatchCriteria(expressionResolver.resolveExpressions(value));
            }
            case SUBNET_MATCH: {
                ModelNode value = parsePossibleExpression(property.getValue());
                return createSubnetMatchCriteria(expressionResolver.resolveExpressions(value));
            }
            case ANY:
            case NOT:
                if(nested) {
                    throw new ParsingException(ControllerLogger.ROOT_LOGGER.nestedElementNotAllowed(element));
                }
                return parseNested(property.getValue(), element == Element.ANY, expressionResolver);
            default:
                throw new ParsingException(ControllerLogger.ROOT_LOGGER.unknownCriteriaInterfaceType(property.getName()));
        }
    }

    private static InterfaceCriteria parseNested(final ModelNode subModel, final boolean any,
                                                 final ExpressionResolver expressionResolver) throws OperationFailedException {
        if(!subModel.isDefined() || subModel.asInt() == 0) {
            return null;
        }
        final Set<InterfaceCriteria> criteriaSet = new LinkedHashSet<InterfaceCriteria>();
        for(final Property nestedProperty :  subModel.asPropertyList()) {
            final Element element = Element.forName(nestedProperty.getName());
            switch (element) {
                case INET_ADDRESS:
                case NIC :
                case NIC_MATCH:
                case SUBNET_MATCH: {
                    if (nestedProperty.getValue().getType() == ModelType.LIST) {
                        for (ModelNode item : nestedProperty.getValue().asList()) {
                            Property prop = new Property(nestedProperty.getName(), item);
                            InterfaceCriteria itemCriteria = parseCriteria(prop, true, expressionResolver);
                            if(itemCriteria != null) {
                                criteriaSet.add(itemCriteria);
                            }
                        }
                        break;
                    } // else drop down into default: block
                }
                default: {
                    final InterfaceCriteria criteria = parseCriteria(nestedProperty, true, expressionResolver);
                    if(criteria != null) {
                        criteriaSet.add(criteria);
                    }
                }
            }
        }
        if(criteriaSet.isEmpty()) {
            return null;
        }
        return any ? new AnyInterfaceCriteria(criteriaSet) : new NotInterfaceCriteria(criteriaSet);
    }

    private static InterfaceCriteria createInetAddressCriteria(final ModelNode model,
                                                               final ExpressionResolver expressionResolver) throws ParsingException, OperationFailedException {
        InetAddress address = parseInetAddress(model, expressionResolver);
        if (address.isAnyLocalAddress()) {
            // they've entered a wildcard address
            return new WildcardInetAddressInterfaceCriteria(address);
        } else if (address.isLoopbackAddress()) {
            // support any loopback address via the -b argument, without xml files changes - WFLY-248
            return new LoopbackAddressInterfaceCriteria(address);
        } else {
            return new InetAddressMatchInterfaceCriteria(address);
        }
    }

    private static InterfaceCriteria createNicMatchCriteria(final ModelNode model) throws ParsingException {
        try {
            Pattern pattern = Pattern.compile(model.asString());
            return new NicMatchInterfaceCriteria(pattern);
        } catch (PatternSyntaxException e) {
            throw new ParsingException(ControllerLogger.ROOT_LOGGER.invalidInterfaceCriteriaPattern(model.asString(), Element.NIC_MATCH.getLocalName()));
        }
    }

    private static InterfaceCriteria createSubnetMatchCriteria(final ModelNode model) throws ParsingException {
        String value;
        String[] split = null;
        try {
            value = model.asString();
            split = value.split("/");
            if (split.length != 2) {
                throw new ParsingException(ControllerLogger.ROOT_LOGGER.invalidAddressMaskValue(value));
            }
            // todo - possible DNS hit here
            final InetAddress addr = InetAddress.getByName(split[0]);
            // Validate both parts of the split
            final byte[] net = addr.getAddress();
            final int mask = Integer.parseInt(split[1]);
            return new SubnetMatchInterfaceCriteria(net, mask);
        } catch (final NumberFormatException e) {
            throw new ParsingException(ControllerLogger.ROOT_LOGGER.invalidAddressMask(split[1], e.getLocalizedMessage()));
        } catch (final UnknownHostException e) {
            throw new ParsingException(ControllerLogger.ROOT_LOGGER.invalidAddressValue(split[0], e.getLocalizedMessage()));
        }
    }

    private static InetAddress parseInetAddress(final ModelNode model, final ExpressionResolver expressionResolver) throws OperationFailedException {
        final String rawAddress = expressionResolver.resolveExpressions(model).asString();
        try {
            return InetAddress.getByName(rawAddress);
        } catch (UnknownHostException e) {
            throw new ParsingException(ControllerLogger.ROOT_LOGGER.invalidAddress(model.asString(), e.getLocalizedMessage()));
        }
    }

    private static void checkStringType(ModelNode node, String id) {
        checkStringType(node, id, false);
    }

    private static void checkStringType(ModelNode node, String id, boolean allowExpressions) {
        if (node.getType() != ModelType.STRING && (!allowExpressions || node.getType() != ModelType.EXPRESSION)) {
            throw new ParsingException(ControllerLogger.ROOT_LOGGER.illegalValueForInterfaceCriteria(node.getType(), id, ModelType.STRING));
        }
    }
    private static ModelNode parsePossibleExpression(final ModelNode node) {
        return (node.getType() == ModelType.STRING) ? ParseUtils.parsePossibleExpression(node.asString()) : node;
    }

    private static class ParsingException extends RuntimeException {
        private static final long serialVersionUID = -5627251228393035383L;

        private final String msg;

        private ParsingException(String msg) {
            this.msg = msg;
        }
    }
}
