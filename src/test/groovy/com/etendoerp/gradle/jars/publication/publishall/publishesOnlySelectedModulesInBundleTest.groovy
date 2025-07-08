package com.etendoerp.gradle.jars.publication.publishall

import com.etendoerp.gradle.jars.publication.PublicationUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.publication.buildfile.ModuleBuildTemplateLoader
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir
import spock.lang.Title

@Title("Running the 'publishAll' task publish all the source modules including bundle modules")
class publishesOnlySelectedModulesInBundleTest extends EtendoCoreResolutionSpecificationTest {

    static final String ENVIRONMENTS_LOCATION = "src/test/resources/jars/multiplepublication/updatepublication/modules"
    static final String REPOSITORY = "etendo-multiplepublish-test"
    static final String REPOSITORY_URL = "https://repo.futit.cloud/repository/"

    @TempDir
    File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def cleanupSpec() {
        PublicationUtils.cleanRepositoryModules(REPOSITORY)
    }

    def "Publishing all modules and then a minor version of moduleC should not increment other modules"() {

        given: "The user with modules to publish A, B and C"
        def modulesDir = new File(testProjectDir, "modules")
        modulesDir.mkdirs()

        def moduleA = "com.test.moduleApublish"
        def moduleB = "com.test.moduleBpublish"
        def moduleC = "com.test.moduleCpublish"
        def moduleBundle = "com.test.module.bundle"

        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleA}"), modulesDir)
        File moduleALocation = new File(testProjectDir, "modules/${moduleA}")
        assert moduleALocation.exists(): "Module A directory does not exist: ${moduleALocation.absolutePath}"
        File buildFileA = new File(moduleALocation, "build.gradle")
        if (buildFileA.exists()) {
            buildFileA.delete()
        }

        and: "The module B which depends on A, declared in the 'AD_MODULE_DEPENDENCY.xml' file"
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleB}"), modulesDir)
        File moduleBLocation = new File(testProjectDir, "modules/${moduleB}")
        assert moduleBLocation.exists(): "Module B directory does not exist: ${moduleBLocation.absolutePath}"
        File buildFileB = new File(moduleBLocation, "build.gradle")
        if (buildFileB.exists()) {
            buildFileB.delete()
        }

        and: "The module C without dependencies"
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleC}"), modulesDir)
        File moduleCLocation = new File(testProjectDir, "modules/${moduleC}")
        assert moduleCLocation.exists(): "Module C directory does not exist: ${moduleCLocation.absolutePath}"
        File buildFileC = new File(moduleCLocation, "build.gradle")
        if (buildFileC.exists()) {
            buildFileC.delete()
        }

        when: "The user runs the 'createModuleBuild' task with the command line parameter '-Ppkg=all'"
        def resultCreateModuleBuild = runTask(":createModuleBuild",
                "-P${com.etendoerp.publication.PublicationUtils.MODULE_NAME_PROP}=${ModuleBuildTemplateLoader.ALL_COMMAND_PROPERTY}",
                "-P${com.etendoerp.publication.PublicationUtils.REPOSITORY_NAME_PROP}=${REPOSITORY_URL}${REPOSITORY}")

        then: "The task will finish successfully"
        assert resultCreateModuleBuild.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "The Bundle which includes modules A, B and C as dependencies"
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleBundle}"), modulesDir)
        File moduleBundleLocation = new File(testProjectDir, "modules/${moduleBundle}")
        assert moduleBundleLocation.exists(): "Module Bundle directory does not exist: ${moduleBundleLocation.absolutePath}"

        and: "The build.gradle files will be created for all modules including the bundle"
        File moduleALocationAfterCreation = new File(testProjectDir, "modules/${moduleA}")
        File buildFileAfterCreationModuleA = new File(moduleALocationAfterCreation, "build.gradle")
        assert buildFileAfterCreationModuleA.exists()
        fixCoreVersion(buildFileAfterCreationModuleA, getCurrentCoreVersion())

        File moduleBLocationAfterCreation = new File(testProjectDir, "modules/${moduleB}")
        File buildFileAfterCreationModuleB = new File(moduleBLocationAfterCreation, "build.gradle")
        assert buildFileAfterCreationModuleB.exists()
        fixCoreVersion(buildFileAfterCreationModuleB, getCurrentCoreVersion())

        File moduleCLocationAfterCreation = new File(testProjectDir, "modules/${moduleC}")
        File buildFileAfterCreationModuleC = new File(moduleCLocationAfterCreation, "build.gradle")
        assert buildFileAfterCreationModuleC.exists()
        fixCoreVersion(buildFileAfterCreationModuleC, getCurrentCoreVersion())

        File moduleBundleLocationAfterCreation = new File(testProjectDir, "modules/${moduleBundle}")
        File buildFileAfterCreationmoduleBundle = new File(moduleBundleLocationAfterCreation, "build.gradle")
        def moduleBundleDir = new File(testProjectDir, "modules/${moduleBundle}")
        def modules = [
                'com.test.moduleApublish',
                'com.test.moduleCpublish',
                'com.test.moduleBpublish'
        ]
        File extensionModulesFile = createExtensionModulesFileInModule(moduleBundleDir, modules)
        assert extensionModulesFile.exists(): "Extension modules file does not exist: ${extensionModulesFile.absolutePath}"

        assert buildFileAfterCreationmoduleBundle.exists()
        fixCoreVersion(buildFileAfterCreationmoduleBundle, getCurrentCoreVersion())

        when: "The user publishes all modules with version 2.0.0"
        def runPublishAllResult = runTask(":publishAll",
                "-P${com.etendoerp.publication.PublicationUtils.MODULE_NAME_PROP}=${moduleBundle}",
                "-PupdateLeaf=true",
                "-Pupdate=major")

        then: "The task will finish successfully"
        assert runPublishAllResult.task(":publishAll").outcome == TaskOutcome.SUCCESS

        and: "Validate module publication contents for version 2.0.0"
        PublicationUtils.validateModuleContents(moduleA, "2.0.0", "com/test/moduleApublish/ModuleA.class",
                "com.test.moduleApublish/src/com/test/moduleApublish/ModuleA.java", REPOSITORY, REPOSITORY_URL, testProjectDir)
        PublicationUtils.validateModuleContents(moduleB, "2.0.0", "com/test/ModuleB.class",
                "com.test.moduleBpublish/src/com/test/ModuleB.java", REPOSITORY, REPOSITORY_URL, testProjectDir)
        PublicationUtils.validateModuleContents(moduleC, "2.0.0", "com/test/ModuleC.class",
                "com.test.moduleCpublish/src/com/test/ModuleC.java", REPOSITORY, REPOSITORY_URL, testProjectDir)

        when: "Remove modules A and C from the temporary directory to simulate publishing only moduleB"
        def moduleADir = new File(testProjectDir, "modules/${moduleA}")
        def moduleBDir = new File(testProjectDir, "modules/${moduleB}")

        if (moduleADir.exists()) {
            FileUtils.deleteDirectory(moduleADir)
        }
        if (moduleBDir.exists()) {
            FileUtils.deleteDirectory(moduleBDir)
        }

        and: "The user publishes only moduleC with a minor version update through the bundle"
        def runPublishMinorResult = runTask(":publishAll",
                "-P${com.etendoerp.publication.PublicationUtils.MODULE_NAME_PROP}=${moduleBundle}",
                "-PupdateLeaf=true",
                "-Pupdate=minor")

        then: "The task will finish successfully"
        assert runPublishMinorResult.task(":publishAll").outcome == TaskOutcome.SUCCESS

        and: "Validate module publication contents for the updated versions"
        PublicationUtils.validateModuleContents(moduleC, "2.1.0", "com/test/ModuleC.class",
                "com.test.moduleCpublish/src/com/test/ModuleC.java", REPOSITORY, REPOSITORY_URL, testProjectDir)

        and: "Other modules remain at version 2.0.0 and were not republished"
        Map<String, List<String>> expectedModulesData = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        expectedModulesData.putAll([
                (moduleA)     : ["2.0.0"],
                (moduleB)     : ["2.0.0"],
                (moduleC)     : ["2.0.0", "2.1.0"],
                (moduleBundle): ["2.0.0", "2.1.0"]
        ])

        and: "Verify repository contains exactly the expected module versions"
        PublicationUtils.repoContainsModules(REPOSITORY, expectedModulesData)

    }
}
