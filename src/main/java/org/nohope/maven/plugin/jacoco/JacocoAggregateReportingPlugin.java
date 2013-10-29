package org.nohope.maven.plugin.jacoco;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * @author <a href="mailto:ketoth.xupack@gmail.com">Ketoth Xupack</a>
 * @since 2013-10-28 12:51
 */
@Mojo(name = "report",
      aggregator = true,
      defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST
)
public class JacocoAggregateReportingPlugin extends AbstractJacocoPlugin {

    @Parameter
    private String groupDirectory;

    @Override
    protected File getOutputDirectory(final MavenProject project) {
        return join(null, project.getModel().getReporting().getOutputDirectory(), "jacoco", groupDirectory);
    }
}
