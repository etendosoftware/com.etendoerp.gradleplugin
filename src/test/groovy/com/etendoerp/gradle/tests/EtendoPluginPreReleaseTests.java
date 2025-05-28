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
        AntJarTasksTest.class,
        AntWarTaskTest.class,
        CompilationTasksCoreConsistencyTest.class,
        CompilationTasksIgnoreConsistencyVerificationTest.class,
        CoreUpdateOldVersionTest.class,
        ModuleExtractionVerificationTest.class,
        ModuleUpdateOldVersionTest.class,//13 min
        JarCoreExportConfigScriptTest.class,//8 min
        JarCoreExportDatabaseTest.class,//12min
        JarCoreExportSampleDataTest.class,//7 min
        SourceCoreJarModuleDeployTest.class,//4 min
        SourceCoreJarModuleInstallTest.class,//7min
        SourceCoreJarModuleTest.class,//4 min
        ModuleValidationsTest.class,//12 min
        ExpandModulesNoOverwriteTest.class,//2min
        ExpandModulesPkgFlagTest.class,//2min
        ExpandTest.class,//33 sec
        ExpandCoreUpdateVersion.class,//5 min
        CoreExpandDeleteJarModuleTest.class,//4min
        CoreRemoveOldVersionInsideWebContentTest.class,//6min
        ModuleRemoveOldVersionInsideWebContentTest.class,//10 min
        AntTasksTest.class,//3min
        DepsTest.class,//13min
        CompileFilesCheckTest.class,//6min
        CreateOBPropertiesTest.class,//25 sec
        CreateModuleBuildTest.class//18 sec

})

public class EtendoPluginPreReleaseTests {
}
