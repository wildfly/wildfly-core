/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.controller.registry;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AliasEntry {

    private final ManagementResourceRegistration target;
    private volatile PathAddress aliasAddress;
    private volatile PathAddress targetAddress;

    protected AliasEntry(final ManagementResourceRegistration target) {
        this.target = target;
    }

    ManagementResourceRegistration getTarget() {
        return target;
    }

    void setAddresses(PathAddress targetAddress, PathAddress aliasAddress) {
        this.targetAddress = targetAddress;
        this.aliasAddress = aliasAddress;
    }

    /**
     * Gets the address to which this alias is registered
     *
     * @return the alias address
     */
    protected PathAddress getAliasAddress() {
        return aliasAddress;
    }

    /**
     * Gets the address to which this alias should convert
     *
     * @return the target address
     */
    protected PathAddress getTargetAddress() {
        return targetAddress;
    }

    /**
     * Convert the alias address to the target address.
     *
     * @param aliasAddress the alias address
     * @param aliasContext the context
     * @return the target address
     */
    public abstract PathAddress convertToTargetAddress(PathAddress aliasAddress, AliasContext aliasContext);

    /**
     * A wrapper around {@link OperationContext} for the requested alias address, allowing extra
     * contextual information when converting alias addresses.
     */
    public static class AliasContext {
        public static final String RECURSIVE_GLOBAL_OP = "recursive-global-op";

        final ResourceProvider delegate;
        final ModelNode operation;

        private AliasContext(final ModelNode operation, final ResourceProvider delegate) {
            this.delegate = delegate;
            this.operation = operation.clone();
            this.operation.protect();
        }

        static AliasContext create(final ModelNode operation, final OperationContext delegate) {
            return new AliasContext(operation, new ResourceProvider() {
                @Override
                public Resource readResourceFromRoot(PathAddress address) {
                    return delegate.readResourceFromRoot(address);
                }

                @Override
                public Resource readResourceFromRoot(PathAddress address, boolean recursive) {
                    return delegate.readResourceFromRoot(address, recursive);
                }
            });
        }

        public static AliasContext create(final PathAddress address, final OperationContext delegate) {
            return create(Util.createEmptyOperation(RECURSIVE_GLOBAL_OP, address), delegate);
        }

        public static AliasContext create(final ModelNode operation, final TransformationContext delegate) {
            return new AliasContext(operation, new ResourceProvider() {
                @Override
                public Resource readResourceFromRoot(PathAddress address) {
                    return delegate.readResourceFromRoot(address);
                }

                @Override
                public Resource readResourceFromRoot(PathAddress address, boolean recursive) {
                    return delegate.readResourceFromRoot(address); //I think this is always recursive
                }
            });
        }

        public static AliasContext create(ModelNode operation, Transformers.TransformationInputs transformationInputs) {
            return new AliasContext(operation, new ResourceProvider() {
                @Override
                public Resource readResourceFromRoot(PathAddress address) {
                    return readResourceFromRoot(address, false);
                }

                @Override
                public Resource readResourceFromRoot(PathAddress address, boolean recursive) {
                    Resource resource = transformationInputs.getRootResource().navigate(address);
                    if (resource == null) {
                        return resource;
                    }
                    if (recursive) {
                        return resource.clone();
                    }
                    final Resource copy = Resource.Factory.create();
                    copy.writeModel(resource.getModel());
                    for(final String childType : resource.getChildTypes()) {
                        for(final Resource.ResourceEntry child : resource.getChildren(childType)) {
                            copy.registerChild(child.getPathElement(), PlaceholderResource.INSTANCE);
                        }
                    }
                    return copy;
                }
            });
        }

        /**
         * @see OperationContext#readResourceFromRoot(PathAddress)
         */
        public Resource readResourceFromRoot(final PathAddress address) {
            return delegate.readResourceFromRoot(address);
        }

        /**
         * @see OperationContext#readResource(PathAddress, boolean)
         */
        public Resource readResourceFromRoot(final PathAddress address, final boolean recursive) {
            return delegate.readResourceFromRoot(address, recursive);
        }

        /**
         * Gets the operation being called. The operation is protected, so you cannot modify it.
         * For the global operations when processing children recurively it will be a placeholder operation whose name is
         * {@link #RECURSIVE_GLOBAL_OP} and the address is the address of the requested aliased resource.
         *
         * @return the operation
         */
        public ModelNode getOperation() {
            return operation;
        }

    }

    private interface ResourceProvider {
        Resource readResourceFromRoot(final PathAddress address);

        Resource readResourceFromRoot(final PathAddress address, final boolean recursive);
    }
}
