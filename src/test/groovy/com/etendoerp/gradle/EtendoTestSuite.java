package com.etendoerp.gradle;

import com.etendoerp.gradle.jars.configuration.CopyConfigDirFromEtendoCoreJarTest;
import com.etendoerp.gradle.jars.dependencies.ProjectWithEtendoCoreDependency;
import com.etendoerp.gradle.jars.expand.expandModules.ExpandModulesNoOverwriteTest;
import com.etendoerp.gradle.jars.expand.expandModules.ExpandModulesPkgFlagTest;
import com.etendoerp.gradle.jars.expand.expandModules.ExpandModulesPkgNotFoundTest;
import com.etendoerp.gradle.jars.expand.expandModules.ExpandModulesWithoutOverwriteTransitivesTest;
import com.etendoerp.gradle.jars.extractresources.ExtractResourcesOfCoreJarTest;
import com.etendoerp.gradle.jars.publication.NexusPublicationTest;
import com.etendoerp.gradle.jars.publication.buildfile.BuildFileCreationAllModulesTest;
import com.etendoerp.gradle.jars.publication.buildfile.BundleBuildFileCreationTest;
import com.etendoerp.gradle.jars.publication.files.BuildDirPublication;
import com.etendoerp.gradle.jars.publication.multiplepublication.MultiplePublicationTest;
import com.etendoerp.gradle.jars.resolution.automaticupdate.CoreJarAutomaticUpdate;
import com.etendoerp.gradle.jars.resolution.compilation.ModulesCompilationTest;
import com.etendoerp.gradle.jars.resolution.coreconflicts.CoreConflictsTest;
import com.etendoerp.gradle.jars.resolution.expand.ExpandCoreWithResolution;
import com.etendoerp.gradle.jars.resolution.modules.CoreExpandDeleteJarModuleTest;
import com.etendoerp.gradle.jars.resolution.modules.CoreExpandTransitiveModulesTest;
import com.etendoerp.gradle.jars.resolution.modules.CoreModuleSkipExtractionTest;
import com.etendoerp.gradle.jars.resolution.modules.CoreTransitiveJarModulesResolutionTest;
import com.etendoerp.gradle.tests.EtendoSpecificationTest;
import com.etendoerp.gradle.tests.ant.DependenciesToAntForCopyTest;
import com.etendoerp.gradle.tests.configuration.CompileFilesCheckTest;
import com.etendoerp.gradle.tests.configuration.CreateOBPropertiesTest;
import com.etendoerp.gradle.tests.configuration.SetupCoreJarTest;
import com.etendoerp.gradle.tests.configuration.SetupOmitCredentialsTest;
import com.etendoerp.gradle.tests.installation.ExpandCustomModuleTest;
import com.etendoerp.gradle.tests.installation.ExpandTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;


@Suite
@SelectClasses({
        CopyConfigDirFromEtendoCoreJarTest.class,
        BuildFileCreationAllModulesTest.class,
        ProjectWithEtendoCoreDependency.class,
        ExpandModulesNoOverwriteTest.class,
        ExpandModulesPkgFlagTest.class,
        ExpandModulesPkgNotFoundTest.class,
        ExpandModulesWithoutOverwriteTransitivesTest.class,
        ExtractResourcesOfCoreJarTest.class,
        NexusPublicationTest.class,
        BundleBuildFileCreationTest.class,
        BuildDirPublication.class,
        MultiplePublicationTest.class,
        CoreJarAutomaticUpdate.class,
        ModulesCompilationTest.class,//se ignoro un test
        CoreConflictsTest.class,
        ExpandCoreWithResolution.class,
        CoreExpandDeleteJarModuleTest.class,
        CoreExpandTransitiveModulesTest.class,
        CoreModuleSkipExtractionTest.class,
        CoreTransitiveJarModulesResolutionTest.class,
        EtendoSpecificationTest.class,
        DependenciesToAntForCopyTest.class,
        CompileFilesCheckTest.class,
        CreateOBPropertiesTest.class,
        SetupCoreJarTest.class,
        SetupOmitCredentialsTest.class,
        ExpandCustomModuleTest.class,
        ExpandTest.class,



})
public class EtendoTestSuite {
}