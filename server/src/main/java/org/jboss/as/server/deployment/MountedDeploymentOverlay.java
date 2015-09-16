package org.jboss.as.server.deployment;

import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;
import org.xnio.IoUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class MountedDeploymentOverlay implements Closeable {

    private Closeable closeable;
    private final File realFile;
    private final VirtualFile mountPoint;

    public MountedDeploymentOverlay(Closeable closeable, File realFile, VirtualFile mountPoint) {
        this.closeable = closeable;
        this.realFile = realFile;
        this.mountPoint = mountPoint;
    }

    public void remountAsZip() throws IOException {
        IoUtils.safeClose(closeable);
        closeable = VFS.mountZip(realFile, mountPoint);
    }

    public File getFile() {
        return realFile;
    }

    @Override
    public void close() throws IOException {
        closeable.close();
    }
}
