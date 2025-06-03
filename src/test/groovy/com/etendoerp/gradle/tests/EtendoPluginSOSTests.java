package com.etendoerp.gradle.tests;

import com.etendoerp.gradle.jars.configuration.CopyConfigDirFromEtendoCoreJarTest;
import com.etendoerp.gradle.jars.configuration.PrepareConfigJarTest;
import com.etendoerp.gradle.jars.consistency.CoreUpdateOldVersionTest;
import com.etendoerp.gradle.jars.consistency.compilationtasks.CompilationTasksConsistencyVerificationTest;
import com.etendoerp.gradle.jars.core.coreinjars.JarCoreCompilationTasksTest;
import com.etendoerp.gradle.jars.core.coreinjars.JarCoreModulesInstallTest;
import com.etendoerp.gradle.jars.core.coreinjars.JarCoreModulesUpdateTest;
import com.etendoerp.gradle.jars.dependencies.ProjectWithEtendoCoreDependency;
import com.etendoerp.gradle.jars.expand.expandModules.ExpandModulesPkgNotFoundTest;
import com.etendoerp.gradle.jars.expand.expandModules.ExpandModulesWithoutOverwriteTransitivesTest;
import com.etendoerp.gradle.jars.extractresources.ExtractResourcesOfCoreJarTest;
import com.etendoerp.gradle.jars.extractresources.ExtractResourcesOfModuleJarTest;
import com.etendoerp.gradle.jars.publication.NexusPublicationTest;
import com.etendoerp.gradle.jars.publication.buildfile.BuildFileCreationAllModulesTest;
import com.etendoerp.gradle.jars.publication.buildfile.BundleBuildFileCreationTest;
import com.etendoerp.gradle.jars.publication.files.BuildDirPublication;
import com.etendoerp.gradle.jars.publication.multiplepublication.MultiplePublicationTest;
import com.etendoerp.gradle.jars.publication.publishall.PublishAllTest;
import com.etendoerp.gradle.jars.resolution.automaticupdate.CoreJarAutomaticUpdate;
import com.etendoerp.gradle.jars.resolution.compilation.ModulesCompilationTest;
import com.etendoerp.gradle.jars.resolution.coreconflicts.CoreConflictsTest;
import com.etendoerp.gradle.jars.resolution.expand.ExpandCoreWithResolution;
import com.etendoerp.gradle.jars.resolution.modules.CoreExpandTransitiveModulesTest;
import com.etendoerp.gradle.jars.resolution.modules.CoreModuleSkipExtractionTest;
import com.etendoerp.gradle.jars.resolution.modules.CoreTransitiveJarModulesResolutionTest;
import com.etendoerp.gradle.jars.resolution.webcontentexclusion.CoreInstallSkipJarToWebContentTest;
import com.etendoerp.gradle.tests.ant.DependenciesToAntForCopyTest;
import com.etendoerp.gradle.tests.configuration.PrepareConfigTest;
import com.etendoerp.gradle.tests.configuration.SetupCoreJarTest;
import com.etendoerp.gradle.tests.configuration.SetupOmitCredentialsTest;
import com.etendoerp.gradle.tests.configuration.SetupTest;
import com.etendoerp.gradle.tests.installation.ExpandCustomModuleTest;
import com.etendoerp.gradle.tests.installation.ExpandTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        JarCoreCompilationTasksTest.class,
        CoreUpdateOldVersionTest.class,
        PrepareConfigJarTest.class,
        JarCoreModulesInstallTest.class,
        JarCoreModulesUpdateTest.class,
        //NexusPublicationTest.class, fallo al publicar core y modulos
        //PublishAllTest.class, El plugin no está configurando automáticamente que generateModuleZip dependa de compileJava
        CoreConflictsTest.class,
        CoreTransitiveJarModulesResolutionTest.class,
        CompilationTasksConsistencyVerificationTest.class,
        CopyConfigDirFromEtendoCoreJarTest.class,
        ProjectWithEtendoCoreDependency.class,
        ExpandModulesPkgNotFoundTest.class,
        ExpandModulesWithoutOverwriteTransitivesTest.class,
        ExtractResourcesOfCoreJarTest.class,
        ExtractResourcesOfModuleJarTest.class,
        //MultiplePublicationTest.class, El test nunca llega a probar esta funcionalidad porque falla en el primer paso de publicación debido al problema de dependencias de tareas.
        //BuildDirPublication.class, La tarea generateModuleZip está intentando usar el output de la tarea compileJava sin declarar explícitamente esta dependencia.
        BundleBuildFileCreationTest.class,
        BuildFileCreationAllModulesTest.class,
        CoreJarAutomaticUpdate.class,
        //ModulesCompilationTest.class, el sistema de resolución de módulos no está respetando el aislamiento esperado entre versiones cuando se usa coreType: "jar".
        CoreExpandTransitiveModulesTest.class,
        ExpandCoreWithResolution.class,
        CoreModuleSkipExtractionTest.class,
        CoreInstallSkipJarToWebContentTest.class,
        EtendoSpecificationTest.class,
        DependenciesToAntForCopyTest.class,
        PrepareConfigTest.class,
        SetupCoreJarTest.class,
        SetupOmitCredentialsTest.class,
        SetupTest.class,
        ExpandCustomModuleTest.class,
        ExpandTest.class

})
public class EtendoPluginSOSTests {

}