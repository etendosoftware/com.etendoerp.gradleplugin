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
@Title("Running the 'publishAll' task publish all the source modules")
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

    def "Publishing all the source modules"() {
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

        //reemplazar en los 3 build.gradle de modulos "x.y.z)" por "versionCore del gradle.properties"+]


        and: "The build.gradle files will be created"
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

        when: "The user runs the 'publishAll' task"
        def runPublishAllResult = runTask(":publishAll")

        then: "The task will finish successfully"
        assert runPublishAllResult.task(":publishAll").outcome == TaskOutcome.SUCCESS

        and: "The modules will be published to the nexus repository"
        Map<String, List<String>> modulesData = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        def modA = modulesData.put(moduleA, ["1.0.0"])
        def modB = modulesData.put(moduleB, ["1.0.0"])
        def modC = modulesData.put(moduleC, ["1.0.0"])
        PublicationUtils.repoContainsModules(REPOSITORY, modulesData)
    }
}
