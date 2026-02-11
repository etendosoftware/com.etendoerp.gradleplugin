package com.etendoerp.gradle.tests;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

import com.etendoerp.automation.ConfigurationServiceSpec;
import com.etendoerp.automation.DockerServiceSpec;
import com.etendoerp.automation.GradleControllerLoaderSpec;
import com.etendoerp.connections.DBCPDataSourceFactorySpec;
import com.etendoerp.connections.DatabaseConnectionExtSpec;
import com.etendoerp.connections.DatabaseConnectionSpec;
import com.etendoerp.connections.DatabasePropertiesSpec;
import com.etendoerp.connections.DatabaseTypeSpec;
import com.etendoerp.consistency.ArtifactInconsistentExceptionSpec;
import com.etendoerp.consistency.EtendoArtifactsComparatorSpec;
import com.etendoerp.consistency.EtendoArtifactsConsistencyContainerSpec;
import com.etendoerp.consistency.VersionStatusSpec;
import com.etendoerp.gradle.jars.antjar.AntJarTasksTest;
import com.etendoerp.gradle.jars.antwar.AntWarTaskTest;
import com.etendoerp.gradle.jars.consistency.CoreUpdateOldVersionTest;
import com.etendoerp.gradle.jars.consistency.ModuleExtractionVerificationTest;
import com.etendoerp.gradle.jars.consistency.ModuleUpdateOldVersionTest;
import com.etendoerp.gradle.jars.consistency.compilationtasks.CompilationTasksCoreConsistencyTest;
import com.etendoerp.gradle.jars.consistency.compilationtasks.CompilationTasksIgnoreConsistencyVerificationTest;
import com.etendoerp.gradle.jars.core.coreinjars.JarCoreExportConfigScriptTest;
import com.etendoerp.gradle.jars.core.coreinjars.JarCoreExportDatabaseTest;
import com.etendoerp.gradle.jars.core.coreinjars.JarCoreExportSampleDataTest;
import com.etendoerp.gradle.jars.core.coreinsources.SourceCoreJarModuleDeployTest;
import com.etendoerp.gradle.jars.core.coreinsources.SourceCoreJarModuleInstallTest;
import com.etendoerp.gradle.jars.core.coreinsources.SourceCoreJarModuleTest;
import com.etendoerp.setup.SetupApplyTemplatesTaskSpec;
import com.etendoerp.setup.SetupLoaderSpec;
import com.etendoerp.setup.applicator.DependencyApplicatorSpec;
import com.etendoerp.setup.applicator.ModuleApplicatorSpec;
import com.etendoerp.setup.applicator.PropertyApplicatorSpec;
import com.etendoerp.setup.applicator.TemplateApplicatorSpec;
import com.etendoerp.setup.template.TemplateParserSpec;
import com.etendoerp.setup.template.TemplateResolverSpec;
import com.etendoerp.setup.template.TemplateSectionSpec;
import com.etendoerp.setup.template.TemplateSpec;

@Suite
@SelectClasses({
        AntJarTasksTest.class,
        AntWarTaskTest.class,
        CompilationTasksCoreConsistencyTest.class,
        CompilationTasksIgnoreConsistencyVerificationTest.class,
        CoreUpdateOldVersionTest.class,
        ModuleExtractionVerificationTest.class,
        ModuleUpdateOldVersionTest.class,
        JarCoreExportConfigScriptTest.class,
        JarCoreExportDatabaseTest.class,
        JarCoreExportSampleDataTest.class,
        SourceCoreJarModuleDeployTest.class,
        SourceCoreJarModuleInstallTest.class,
        SourceCoreJarModuleTest.class,
        SetupApplyTemplatesTaskSpec.class,
        SetupLoaderSpec.class,
        TemplateApplicatorSpec.class,
        DependencyApplicatorSpec.class,
        ModuleApplicatorSpec.class,
        PropertyApplicatorSpec.class,
        TemplateResolverSpec.class,
        TemplateSpec.class,
        TemplateSectionSpec.class,
        TemplateParserSpec.class,
        DockerServiceSpec.class,
        GradleControllerLoaderSpec.class,
        ConfigurationServiceSpec.class,
        DBCPDataSourceFactorySpec.class,
        DatabaseConnectionSpec.class,
        DatabaseTypeSpec.class,
        DatabasePropertiesSpec.class,
        DatabaseConnectionExtSpec.class,
        EtendoArtifactsConsistencyContainerSpec.class,
        ArtifactInconsistentExceptionSpec.class,
        EtendoArtifactsComparatorSpec.class,
        VersionStatusSpec.class
})

public class EtendoPluginPreReleaseTestsPart1 {
}
