package com.etendoerp.gradle.tests;

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
import com.etendoerp.gradle.jars.dependencies.buildvalidations.ModuleValidationsTest;
import com.etendoerp.gradle.jars.expand.ExpandTest;
import com.etendoerp.gradle.jars.expand.expandModules.ExpandModulesNoOverwriteTest;
import com.etendoerp.gradle.jars.expand.expandModules.ExpandModulesPkgFlagTest;
import com.etendoerp.gradle.jars.resolution.expand.ExpandCoreUpdateVersion;
import com.etendoerp.gradle.jars.resolution.modules.CoreExpandDeleteJarModuleTest;
import com.etendoerp.gradle.jars.resolution.webcontentexclusion.CoreRemoveOldVersionInsideWebContentTest;
import com.etendoerp.gradle.jars.resolution.webcontentexclusion.ModuleRemoveOldVersionInsideWebContentTest;
import com.etendoerp.gradle.tests.ant.AntTasksTest;
import com.etendoerp.gradle.tests.ant.DepsTest;
import com.etendoerp.gradle.tests.configuration.CompileFilesCheckTest;
import com.etendoerp.gradle.tests.configuration.CreateOBPropertiesTest;
import com.etendoerp.gradle.tests.distribution.CreateModuleBuildTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        CreateModuleBuildTest.class,
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
        ModuleValidationsTest.class,
        ExpandModulesNoOverwriteTest.class,
        ExpandModulesPkgFlagTest.class,
        ExpandTest.class,
        ExpandCoreUpdateVersion.class,
        CoreExpandDeleteJarModuleTest.class,
        CoreRemoveOldVersionInsideWebContentTest.class,
        ModuleRemoveOldVersionInsideWebContentTest.class,
        AntTasksTest.class,
        DepsTest.class,
        CompileFilesCheckTest.class,
        CreateOBPropertiesTest.class,

})

public class EtendoPluginPreReleaseTests {
}
