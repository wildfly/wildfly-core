package org.wildfly.extension.io;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.XnioByteBufferPool;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.xnio.Pool;

class BufferPoolAdd extends AbstractAddStepHandler {

    static final BufferPoolAdd INSTANCE = new BufferPoolAdd();

    BufferPoolAdd() {
        super(BufferPoolResourceDefinition.ATTRIBUTES);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));

        final String name = address.getLastElement().getValue();
        final ModelNode bufferSizeModel = BufferPoolResourceDefinition.BUFFER_SIZE.resolveModelAttribute(context, model);

        final ModelNode bufferPerSliceModel = BufferPoolResourceDefinition.BUFFER_PER_SLICE.resolveModelAttribute(context,
                model);
        final ModelNode directModel = BufferPoolResourceDefinition.DIRECT_BUFFERS.resolveModelAttribute(context, model);

        final int bufferSize = bufferSizeModel.isDefined() ? bufferSizeModel.asInt()
                : BufferPoolResourceDefinition.defaultBufferSize;
        final int bufferPerSlice = bufferPerSliceModel.isDefined() ? bufferPerSliceModel.asInt()
                : BufferPoolResourceDefinition.defaultBuffersPerRegion;
        final boolean direct = directModel.isDefined() ? directModel.asBoolean()
                : BufferPoolResourceDefinition.defaultDirectBuffers;

        final BufferPoolService service = new BufferPoolService(bufferSize, bufferPerSlice, direct);
        context.getCapabilityServiceTarget().addCapability(BufferPoolResourceDefinition.IO_POOL_RUNTIME_CAPABILITY, service)
                .setInitialMode(ServiceController.Mode.ON_DEMAND)
                .install();

        ByteBufferPoolService poolService = new ByteBufferPoolService();

        context.getCapabilityServiceTarget().addCapability(BufferPoolResourceDefinition.IO_BYTE_BUFFER_POOL_RUNTIME_CAPABILITY, poolService)
                .addCapabilityRequirement(BufferPoolResourceDefinition.IO_POOL_RUNTIME_CAPABILITY.getDynamicName(address), Pool.class, poolService.bufferPool)
                .setInitialMode(ServiceController.Mode.ON_DEMAND)
                .install();

    }

    private static final class ByteBufferPoolService implements Service<ByteBufferPool> {

        final InjectedValue<Pool> bufferPool = new InjectedValue<>();
        private volatile ByteBufferPool byteBufferPool;

        @Override
        public void start(StartContext startContext) throws StartException {
            byteBufferPool = new XnioByteBufferPool(bufferPool.getValue());
        }

        @Override
        public void stop(StopContext stopContext) {
            byteBufferPool.close();
            byteBufferPool = null;
        }

        @Override
        public ByteBufferPool getValue() throws IllegalStateException, IllegalArgumentException {
            return byteBufferPool;
        }
    }

}
