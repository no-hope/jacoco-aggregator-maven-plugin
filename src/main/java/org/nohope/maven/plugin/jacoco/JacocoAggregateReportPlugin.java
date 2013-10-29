package org.nohope.maven.plugin.jacoco;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkFactory;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenMultiPageReport;
import org.apache.maven.reporting.MavenReportException;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * @author <a href="mailto:ketoth.xupack@gmail.com">Ketoth Xupack</a>
 * @since 2013-10-28 12:51
 */
@Mojo(name = "aggregate",
        aggregator = true,
        defaultPhase = LifecyclePhase.SITE
)
public class JacocoAggregateReportPlugin
        extends AbstractJacocoPlugin
        implements MavenMultiPageReport {

    /**
     * Output directory for the reports. Note that this parameter is only
     * relevant if the goal is run from the command line or from the default
     * build lifecycle. If the goal is run indirectly as part of a site
     * generation, the output directory configured in the Maven Site Plugin is
     * used instead.
     */
    @Parameter(defaultValue = "${project.reporting.outputDirectory}/jacoco", readonly = true)
    private File outputDirectory;

    @Parameter
    private String groupName;

    @Parameter
    private String groupDirectory;

    /**
     * Generate a report.
     *
     * @param sink the sink to use for the generation.
     * @param aLocale the wanted locale to generate the report, could be null.
     * @throws MavenReportException if any
     * @deprecated use {@link #generate(Sink, SinkFactory, Locale)} instead.
     */
    @Override
    public void generate(final org.codehaus.doxia.sink.Sink sink,
                         final Locale aLocale) throws MavenReportException {
        generate(sink, null, aLocale);
    }

    @Override
    public String getOutputName() {
        return StringUtils.join(Arrays.asList("jacoco", groupDirectory, "index"), File.separatorChar);
    }

    @Override
    public String getCategoryName() {
        return "Project Info";
    }

    @Override
    public String getName(final Locale locale) {
        return doPostfix("JaCoCo");
    }

    @Override
    public String getDescription(final Locale locale) {
        return "JaCoCo Test Coverage Report.";
    }

    @Override
    public void setReportOutputDirectory(final File reportOutputDirectory) {
        outputDirectory = reportOutputDirectory;
    }

    @Override
    public void generate(final Sink sink, final SinkFactory factory, final Locale locale)
            throws MavenReportException {
        if (sink == null) {
            throw new MavenReportException("You must specify a sink.");
        }

        if (!canGenerateReport()) {
            getLog().info("This report cannot be generated as part of the current build. "
                          + "The report name should be referenced in this line of output.");
            return;
        }

        executeReport(locale);
    }

    @Override
    public boolean isExternalReport() {
        return true;
    }

    @Override
    public File getReportOutputDirectory() {
        return outputDirectory;
    }

    @Override
    protected File getOutputDirectory(final MavenProject project) {
        return join(null, project.getModel().getReporting().getOutputDirectory(), "jacoco", groupDirectory);
    }

    private String doPostfix(final String string) {
        if (groupName == null || groupName.isEmpty()) {
            return string;
        }

        return string + " (" + groupName + ')';
    }
}
