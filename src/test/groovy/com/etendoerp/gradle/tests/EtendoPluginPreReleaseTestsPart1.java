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
        ModuleUpdateOldVersionTest.class,
        JarCoreExportConfigScriptTest.class,
        JarCoreExportDatabaseTest.class,
        JarCoreExportSampleDataTest.class,
        SourceCoreJarModuleDeployTest.class,
        SourceCoreJarModuleInstallTest.class,
        SourceCoreJarModuleTest.class
})

public class EtendoPluginPreReleaseTestsPart1 {
}
