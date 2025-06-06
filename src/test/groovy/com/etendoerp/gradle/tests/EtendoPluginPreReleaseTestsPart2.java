package com.etendoerp.gradle.tests;

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
        //ModuleValidationsTest.class, The problem is that the build validation system cannot find the compiled classes
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
        CreateModuleBuildTest.class
})

public class EtendoPluginPreReleaseTestsPart2 {
}
