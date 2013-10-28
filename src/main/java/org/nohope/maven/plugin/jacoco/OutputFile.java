package org.nohope.maven.plugin.jacoco;

import java.util.List;

/**
 * @author <a href="mailto:ketoth.xupack@gmail.com">Ketoth Xupack</a>
 * @since 2013-10-28 10:56
 */
public class OutputFile {
    private List<String> execFiles;
    private String outputFile;

    public List<String> getExecFiles() {
        return execFiles;
    }

    public void setExecFiles(final List<String> execFiles) {
        this.execFiles = execFiles;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(final String outputFile) {
        this.outputFile = outputFile;
    }
}
