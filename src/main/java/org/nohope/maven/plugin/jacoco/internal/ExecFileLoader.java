package org.nohope.maven.plugin.jacoco.internal;

import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.data.SessionInfoStore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author <a href="mailto:ketoth.xupack@gmail.com">Ketoth Xupack</a>
 * @since 2013-10-29 10:56
 * @deprecated use {@link org.jacoco.core.data.ExecFileLoader} after 0.6.4 release
 */
@Deprecated
public class ExecFileLoader {
    private final SessionInfoStore sessionInfos;
    private final ExecutionDataStore executionData;

    public ExecFileLoader() {
        sessionInfos = new SessionInfoStore();
        executionData = new ExecutionDataStore();
    }

    public void load(final InputStream stream) throws IOException {
        final ExecutionDataReader reader = new ExecutionDataReader(
                new BufferedInputStream(stream));
        reader.setExecutionDataVisitor(executionData);
        reader.setSessionInfoVisitor(sessionInfos);
        reader.read();
    }

    public void load(final File file) throws IOException {
        final InputStream stream = new FileInputStream(file);
        try {
            load(stream);
        } finally {
            stream.close();
        }
    }

    public void save(final OutputStream stream) throws IOException {
        final ExecutionDataWriter dataWriter = new ExecutionDataWriter(stream);
        sessionInfos.accept(dataWriter);
        executionData.accept(dataWriter);
    }

    public void save(final File file, final boolean append) throws IOException {
        final File folder = file.getParentFile();
        if (folder != null) {
            folder.mkdirs();
        }
        final FileOutputStream fileStream = new FileOutputStream(file, append);
        // Avoid concurrent writes from other processes:
        fileStream.getChannel().lock();
        final OutputStream bufferedStream = new BufferedOutputStream(fileStream);
        try {
            save(bufferedStream);
        } finally {
            bufferedStream.close();
        }
    }

    public SessionInfoStore getSessionInfoStore() {
        return sessionInfos;
    }

    public ExecutionDataStore getExecutionDataStore() {
        return executionData;
    }

}
