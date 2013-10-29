package org.nohope.maven.plugin.jacoco;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.data.ExecFileLoader;
import org.jacoco.maven.BundleCreator;
import org.jacoco.maven.FileFilter;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportGroupVisitor;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.ISourceFileLocator;
import org.jacoco.report.MultiReportVisitor;
import org.jacoco.report.csv.CSVFormatter;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * @author <a href="mailto:ketoth.xupack@gmail.com">Ketoth Xupack</a>
 * @since 2013-10-28 12:51
 */
@Mojo(name = "aggregate",
      aggregator = true,
      defaultPhase = LifecyclePhase.SITE
)
public class JacocoAggregateReportPlugin extends AbstractMavenReport {

    /**
     * Output directory for the reports. Note that this parameter is only
     * relevant if the goal is run from the command line or from the default
     * build lifecycle. If the goal is run indirectly as part of a site
     * generation, the output directory configured in the Maven Site Plugin is
     * used instead.
     */
    @Parameter(defaultValue = "${project.reporting.outputDirectory}/jacoco", readonly = true)
    private File outputDirectory;

    /** Encoding of the generated reports. */
    @Parameter(property = "project.reporting.outputEncoding", defaultValue = "UTF-8")
    private String outputEncoding;

    /** Encoding of the source files. */
    @Parameter(property = "project.build.sourceEncoding", defaultValue = "UTF-8")
    private String sourceEncoding;

    /** File with execution data. */
    @Parameter(defaultValue = "${project.build.directory}/jacoco.exec")
    private File dataFile;

    /** {@code true} to fail on missing data files in {@code dataFiles} list. */
    @Parameter
    private boolean strict = false;

    /** Do not produce report for each module in multimodule project. */
    @Parameter
    private boolean skipModule = false;

    /** Flg for aggregating each module report in multimodule environment. */
    @Parameter
    private boolean aggregate = true;

    @Parameter
    private String reports = "xml,html";

    /**
     * A list of class files to include in the report. May use wildcard
     * characters (* and ?). When not specified everything will be included.
     */
    @Parameter
    private List<String> includes;

    /**
     * A list of class files to exclude from the report. May use wildcard
     * characters (* and ?). When not specified nothing will be excluded.
     */
    @Parameter
    private List<String> excludes;

    /** Flag used to suppress execution. */
    @Parameter(property = "skip.jacoco", defaultValue = "false")
    private boolean skip;

    /** Maven project. */
    @Component
    private MavenProject project;

    /** The projects in the reactor for aggregation report. */
    @Parameter(property = "reactorProjects", readonly = true)
    private List<MavenProject> reactorProjects;

    /** Doxia Site Renderer. */
    @Component
    private Renderer siteRenderer;

    @Parameter(required = true)
    private String groupName;

    @Parameter(required = true)
    private String groupDirectory;

    @Parameter(required = true)
    private List<String> dataFiles;

    @Override
    public String getOutputName() {
        return StringUtils.join(Arrays.asList("jacoco", groupDirectory, "index"), File.separatorChar);
    }

    @Override
    public String getName(final Locale locale) {
        return doPostfix("JaCoCo");
    }

    private String doPostfix(final String string) {
        if (groupName == null || groupName.isEmpty()) {
            return string;
        }

        return string + " (" + groupName + ')';
    }

    @Override
    public String getDescription(final Locale locale) {
        return "JaCoCo Test Coverage Report.";
    }

    /**
     * This method is called when the report generation is invoked directly as a
     * standalone Mojo.
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (!canGenerateReport()) {
            return;
        }
        try {
            executeReport(Locale.getDefault());
        } catch (final MavenReportException e) {
            throw new MojoExecutionException("An error has occurred in "
                                             + getName(Locale.ENGLISH)
                                             + " report generation.", e);
        }
    }

    @Override
    public void setReportOutputDirectory(final File reportOutputDirectory) {
        outputDirectory = reportOutputDirectory;
    }

    @Override
    public boolean isExternalReport() {
        return true;
    }

    @Override
    public boolean canGenerateReport() {
        if (skip) {
            getLog().info("Skipping JaCoCo execution");
            return false;
        }

        // fallback to old behavior
        if (dataFiles == null) {
            skipModule = false;
            strict = true;
            aggregate = true;
        }

        if (dataFiles == null && dataFile != null && !dataFile.exists()) {
            getLog().warn("Skipping JaCoCo execution due to missing execution data file");
            return false;
        }

        return true;
    }

    @Override
    protected Renderer getSiteRenderer() {
        return siteRenderer;
    }

    @Override
    public String getOutputDirectory() {
        return join(outputDirectory, "jacoco", groupDirectory).getAbsolutePath();
    }

    @Override
    protected MavenProject getProject() {
        return project;
    }

    @Override
    protected void executeReport(final Locale locale)
            throws MavenReportException {
        try {
            executeReport(locale, false);
            if (project.equals(getLastProject())) {
                executeReport(locale, true);
            }
        } catch (final IOException e) {
            throw new MavenReportException("Error while creating report: " + e.getMessage(), e);
        }
    }

    private MavenProject getLastProject() {
        final int size = reactorProjects.size();
        return reactorProjects.get(size - 1);
    }

    private void executeReport(final Locale locale, final boolean root)
            throws IOException, MavenReportException {

        final boolean exec = root ? aggregate : !skipModule;
        if (!exec) {
            return;
        }

        final File outputDirectory = getOutputDirectory(root ? getRootProject() : project);

        getLog().info("Jacoco: Generating report " + groupName + " to " + outputDirectory);

        final IReportVisitor mainVisitor = createVisitor(locale, outputDirectory);
        boolean visited = false;

        final ExecFileLoader loader = loadExecutionData(root);
        mainVisitor.visitInfo(
                loader.getSessionInfoStore().getInfos(),
                loader.getExecutionDataStore().getContents());

        if (root) {
            final IReportGroupVisitor subProjectVisitor = mainVisitor.visitGroup(
                    getRootProject().getName());

            for (final MavenProject child : reactorProjects) {
                visited |= visitProject(loader, subProjectVisitor, child);
            }
        } else {
            visited = visitProject(loader, mainVisitor, project);
        }

        if (visited) {
            mainVisitor.visitEnd();
        }
    }

    private IReportVisitor createVisitor(final Locale locale, final File outputDirectory)
            throws IOException {
        final List<IReportVisitor> visitors = new ArrayList<IReportVisitor>();

        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IOException("Unable to create " + outputDirectory);
        }

        if (reports.contains("xml")) {
            final XMLFormatter xmlFormatter = new XMLFormatter();
            xmlFormatter.setOutputEncoding(outputEncoding);
            visitors.add(xmlFormatter.createVisitor(new FileOutputStream(new File(
                    outputDirectory, "jacoco.xml"))));
        }

        if (reports.contains("csv")) {
            final CSVFormatter csvFormatter = new CSVFormatter();
            csvFormatter.setOutputEncoding(outputEncoding);
            visitors.add(csvFormatter.createVisitor(new FileOutputStream(new File(
                    outputDirectory, "jacoco.csv"))));
        }

        if (reports.contains("html")) {
            final HTMLFormatter htmlFormatter = new HTMLFormatter();
            htmlFormatter.setOutputEncoding(outputEncoding);
            htmlFormatter.setLocale(locale);
            visitors.add(htmlFormatter.createVisitor(new FileMultiReportOutput(
                    outputDirectory)));
        }

        return new MultiReportVisitor(visitors);
    }

    /**
     * @return {@code true} if project was actually visited
     *
     * @throws IOException
     */
    private boolean visitProject(final ExecFileLoader loader,
                                 final IReportGroupVisitor visitor,
                                 final MavenProject project)
            throws IOException {

        // skip processing modules with "pom" packaging
        if ("pom".equals(project.getPackaging())) {
            return false;
        }

        final FileFilter fileFilter = new FileFilter(this.includes, this.excludes);
        final BundleCreator creator = new BundleCreator(project, fileFilter);
        final IBundleCoverage bundle = creator.createBundle(loader.getExecutionDataStore());

        final SourceFileCollection locator = new SourceFileCollection(
                getCompileSourceRoots(project),
                sourceEncoding);

        checkForMissingDebugInformation(bundle);
        visitor.visitBundle(bundle, locator);

        return true;
    }

    private static List<File> getCompileSourceRoots(final MavenProject project) {
        final List<File> result = new ArrayList<File>();
        for (final Object path : project.getCompileSourceRoots()) {
            result.add(resolvePath(project, (String) path));
        }
        return result;
    }

    private void checkForMissingDebugInformation(final ICoverageNode node) {
        if (node.getClassCounter().getTotalCount() > 0
            && node.getLineCounter().getTotalCount() == 0) {
            getLog().warn(
                    "To enable source code annotation class files have to be compiled with debug information.");
        }
    }

    private ExecFileLoader loadExecutionData(final boolean root)
            throws MavenReportException {
        final ExecFileLoader loader = new ExecFileLoader();
        try {
            if (root) {
                for (final MavenProject project : reactorProjects) {
                    loadExecFile(loader, project);
                }
            } else {
                loadExecFile(loader, project);
            }
        } catch (final IOException e) {
            throw new MavenReportException("Unable to read execution data file: " + e.getMessage(), e);
        }

        return loader;
    }

    private void loadExecFile(final ExecFileLoader loader, final MavenProject project)
            throws IOException, MavenReportException {
        for (final String dataFile : getDataFiles()) {
            final File file = resolvePath(project, dataFile);
            if (file.exists()) {
                loader.load(file);
            } else {
                if (strict) {
                    throw new MavenReportException("File " + file + " exists");
                }
            }
        }
    }

    private static File resolvePath(final MavenProject project, final String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(project.getBasedir(), path);
        }
        return file;
    }

    private List<String> getDataFiles() {
        if (dataFiles == null && dataFile != null) {
            return Collections.singletonList(dataFile.getPath());
        }
        return dataFiles;
    }

    private MavenProject getRootProject() {
        for (final MavenProject reactorProject : reactorProjects) {
            if (reactorProject.isExecutionRoot()) {
                return reactorProject;
            }
        }
        throw new IllegalStateException("Unable to determine root project");
    }

    private File getOutputDirectory(final MavenProject project) {
        return join(null, project.getModel().getReporting().getOutputDirectory(), "jacoco", groupDirectory);
    }

    private static File join(final File file, final String... parts) {
        final List<String> elements = new ArrayList<String>(Arrays.asList(parts));
        File result = file;
        if (file == null && parts.length > 0) {
            result = new File(elements.remove(0));
        }

        for (final String e : elements) {
            result = new File(result, e);
        }

        return result;
    }

    private static class SourceFileCollection implements ISourceFileLocator {

        private final List<File> sourceRoots;
        private final String encoding;

        public SourceFileCollection(final List<File> sourceRoots,
                                    final String encoding) {
            this.sourceRoots = sourceRoots;
            this.encoding = encoding;
        }

        @Override
        public Reader getSourceFile(final String packageName,
                                    final String fileName) throws IOException {
            final String r;
            if (!packageName.isEmpty()) {
                r = packageName + '/' + fileName;
            } else {
                r = fileName;
            }
            for (final File sourceRoot : sourceRoots) {
                final File file = new File(sourceRoot, r);
                if (file.exists() && file.isFile()) {
                    return new InputStreamReader(new FileInputStream(file),
                            encoding);
                }
            }
            return null;
        }

        @Override
        public int getTabWidth() {
            return 4;
        }
    }

}
