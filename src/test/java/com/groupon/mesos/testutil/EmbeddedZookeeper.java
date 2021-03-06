/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.groupon.mesos.testutil;

import static com.google.common.base.Preconditions.checkState;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.io.Files;
import com.groupon.mesos.util.NetworkUtil;

import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;

public class EmbeddedZookeeper
    implements Closeable
{
    private final int port;
    private final File zkDataDir;
    private final ZooKeeperServer zkServer;
    private final NIOServerCnxn.Factory cnxnFactory;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();

    public EmbeddedZookeeper()
                    throws IOException
    {
        this(NetworkUtil.findUnusedPort());
    }

    public EmbeddedZookeeper(final int port)
                    throws IOException
    {
        this.port = port;
        zkDataDir = Files.createTempDir();
        zkServer = new ZooKeeperServer();

        final FileTxnSnapLog ftxn = new FileTxnSnapLog(zkDataDir, zkDataDir);
        zkServer.setTxnLogFactory(ftxn);

        cnxnFactory = new NIOServerCnxn.Factory(new InetSocketAddress(this.port), 0);
    }

    public void start()
        throws InterruptedException, IOException
    {
        if (!started.getAndSet(true)) {
            cnxnFactory.startup(zkServer);
        }
    }

    @Override
    public void close()
    {
        if (started.get() && !stopped.getAndSet(true)) {
            cnxnFactory.shutdown();
            try {
                cnxnFactory.join();
            }
            catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (zkServer.isRunning()) {
                zkServer.shutdown();
            }
        }
    }

    public String getConnectString()
    {
        return "127.0.0.1:" + Integer.toString(port);
    }

    public int getPort()
    {
        return port;
    }

    public void cleanup()
    {
        checkState(stopped.get(), "not stopped");

        for (final File file : Files.fileTreeTraverser().postOrderTraversal(zkDataDir)) {
            file.delete();
        }
    }
}
