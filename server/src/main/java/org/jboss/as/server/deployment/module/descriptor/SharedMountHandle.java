package org.jboss.as.server.deployment.module.descriptor;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

class SharedMountHandle implements Closeable {
    private static Map<VirtualFile, SharedMountHandle> mountPoints = new HashMap<VirtualFile, SharedMountHandle>();
    private VirtualFile file;
    private Closeable closeable;
    private volatile int numberOfReferences;

    private SharedMountHandle(VirtualFile file, Closeable closeable) {
        this.file = file;
        this.closeable = closeable;
    }

    @Override
    public void close() throws IOException {
        synchronized (mountPoints) {
            if(--numberOfReferences == 0) {
                closeable.close();
                mountPoints.remove(file);
            }
        }
    }

    /**
     * Get a closeable reference from a virtual file if it is a file or is already mount in VFS
     * This reference could be shared among several deploys if they point to the same resource
     * through the jboss-deployment-structure
     * @param child virtual file
     * @return shared closeable reference if the virtual file is a file or is already mount.
     * @throws IOException
     */
    public static Closeable newReference(VirtualFile child) throws IOException {
        synchronized (mountPoints) {
            SharedMountHandle closeable = null;
            if(mountPoints.containsKey(child)) {
                closeable = mountPoints.get(child);
                closeable.numberOfReferences++;
            } else if(child.isFile()) {
                closeable = new SharedMountHandle(child, VFS.mountZip(child, child, TempFileProviderService.provider()));
                mountPoints.put(child, closeable);
                closeable.numberOfReferences++;
            }
            return closeable;
        }
    }
}
