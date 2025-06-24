package com.etendoerp.gradle.jars.publication.publishall

import com.etendoerp.gradle.jars.publication.PublicationUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.publication.buildfile.ModuleBuildTemplateLoader
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.TempDir
import spock.lang.Title

@Issue("EM-62")
@Title("Running the 'publishAll' task publish all the source modules including bundle modules")
class PublishAllTest extends EtendoCoreResolutionSpecificationTest {

    static final String ENVIRONMENTS_LOCATION = "src/test/resources/jars/multiplepublication/updatepublication/modules"
    static final String REPOSITORY = "etendo-multiplepublish-test"
    static final String REPOSITORY_URL = "https://repo.futit.cloud/repository/"

    @TempDir
    File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    // Clean the repository
    def cleanupSpec() {
        PublicationUtils.cleanRepositoryModules(REPOSITORY)
    }

    def "Publishing all the source modules including a bundle module"() {

        given: "The user with modules to publish A, B and C"
        def modulesDir = new File(testProjectDir, "modules")
        modulesDir.mkdirs()

        def moduleA = "com.test.moduleApublish"
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleA}"), modulesDir)
        File moduleALocation = new File(testProjectDir, "modules/${moduleA}")
        // Delete the 'build.gradle' from the module A to be recreated
        File buildFileA = new File(moduleALocation, "build.gradle")
        if (buildFileA.exists()) {
            buildFileA.delete()
        }

        and: "The module B which depends on A, declared in the 'AD_MODULE_DEPENDENCY.xml' file"
        def moduleB = "com.test.moduleBpublish"
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleB}"), modulesDir)
        File moduleBLocation = new File(testProjectDir, "modules/${moduleB}")
        // Delete the 'build.gradle'
        File buildFileB = new File(moduleBLocation, "build.gradle")
        if (buildFileB.exists()) {
            buildFileB.delete()
        }

        and: "The module C without dependencies"
        def moduleC = "com.test.moduleCpublish"
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleC}"), modulesDir)
        File moduleCLocation = new File(testProjectDir, "modules/${moduleC}")
        // Delete the 'build.gradle'
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
        def moduleBundle = "com.test.module.bundle"
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleBundle}"), modulesDir)

        and: "The build.gradle files will be created for all modules including the bundle"
        // Módule A
        File moduleALocationAfterCreation = new File(testProjectDir, "modules/${moduleA}")
        File buildFileAfterCreationModuleA = new File(moduleALocationAfterCreation, "build.gradle")
        assert buildFileAfterCreationModuleA.exists()
        fixCoreVersion(buildFileAfterCreationModuleA, getCurrentCoreVersion())

        // Módule B
        File moduleBLocationAfterCreation = new File(testProjectDir, "modules/${moduleB}")
        File buildFileAfterCreationModuleB = new File(moduleBLocationAfterCreation, "build.gradle")
        assert buildFileAfterCreationModuleB.exists()
        fixCoreVersion(buildFileAfterCreationModuleB, getCurrentCoreVersion())

        // Módule C
        File moduleCLocationAfterCreation = new File(testProjectDir, "modules/${moduleC}")
        File buildFileAfterCreationModuleC = new File(moduleCLocationAfterCreation, "build.gradle")
        assert buildFileAfterCreationModuleC.exists()
        fixCoreVersion(buildFileAfterCreationModuleC, getCurrentCoreVersion())

        // Module Bundle
        File moduleBundleLocationAfterCreation = new File(testProjectDir, "modules/${moduleBundle}")
        File buildFileAfterCreationmoduleBundle = new File(moduleBundleLocationAfterCreation, "build.gradle")
        def moduleBundleDir = new File(testProjectDir, "modules/${moduleBundle}")
        def modules = [
                'com.test.moduleApublish',      // A - Base module (no dependencies)
                'com.test.moduleCpublish',      // C - Independent module (no dependencies)
                'com.test.moduleBpublish'       // B - Depends on A (must come after A)
        ]
        createExtensionModulesFileInModule(moduleBundleDir, modules)
        assert buildFileAfterCreationmoduleBundle.exists()
        fixCoreVersion(buildFileAfterCreationmoduleBundle, getCurrentCoreVersion())

        and: "The bundle module should have dependencies to modules A, B and C"
        String bundleBuildContent = buildFileAfterCreationmoduleBundle.text
        assert bundleBuildContent.contains("dependencies {")
        assert bundleBuildContent.contains("implementation 'com.test:moduleBpublish:1.0.0'")
        assert bundleBuildContent.contains("implementation 'com.test:moduleCpublish:1.0.0'")

        when: "The user runs the 'publishAll' task"
        def runPublishAllResult = runTask(":publishAll", "-P${com.etendoerp.publication.PublicationUtils.MODULE_NAME_PROP}=${moduleBundle}", "-PupdateLeaf=true", "-Pupdate=major")

        then: "The task will finish successfully"
        assert runPublishAllResult.task(":publishAll").outcome == TaskOutcome.SUCCESS

        and: "All modules including the bundle will be published to the nexus repository"
        Map<String, List<String>> modulesData = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        def modA = modulesData.put(moduleA, ["1.0.0"])
        def modB = modulesData.put(moduleB, ["1.0.0"])
        def modC = modulesData.put(moduleC, ["1.0.0"])
        def modBundle = modulesData.put(moduleBundle, ["1.0.0"])
        PublicationUtils.repoContainsModules(REPOSITORY, modulesData)

        and: "The bundle module POM should contain the correct dependencies"
        def bundlePomAddress = "${REPOSITORY_URL}${REPOSITORY}/com/test/module/bundle/1.0.0/module.bundle-1.0.0.pom"
        Map bundlePomDependencies = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        bundlePomDependencies.put(moduleB, ["group":"com.test", "artifact": "moduleBpublish", "version": "1.0.0"])
        bundlePomDependencies.put(moduleC, ["group":"com.test", "artifact": "moduleCpublish", "version": "1.0.0"])
        PublicationUtils.downloadAndValidatePomFile(bundlePomAddress, testProjectDir, "module.bundle-1.0.0.pom", "1.0.0", bundlePomDependencies)
    }
}
