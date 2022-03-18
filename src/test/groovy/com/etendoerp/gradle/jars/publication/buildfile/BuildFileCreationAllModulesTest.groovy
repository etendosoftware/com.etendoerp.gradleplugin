package com.etendoerp.gradle.jars.publication.buildfile

import com.etendoerp.gradle.tests.EtendoSpecification
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.buildfile.ModuleBuildTemplateLoader
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Issue("EM-62")
@Title("The 'createModuleBuild' task should create all the 'build.gradle' files for each module.")
@Narrative("""
Running the 'createModuleBuild' task with the command line parameter '-Ppkg=all' creates the 'build.gradle' file
for each module. The file also includes the dependencies between subprojects.
""")
class BuildFileCreationAllModulesTest extends EtendoSpecification {

    static final String ENVIRONMENTS_LOCATION = "src/test/resources/jars/multiplepublication/updatepublication/modules"
    static final String REPOSITORY = "etendo-multiplepublish-test"
    static final String REPOSITORY_URL = "https://repo.futit.cloud/repository/"

    @TempDir
    File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "Running the 'createModuleBuild' task generates all the 'build gradle' files"() {
        given: "The user with modules to create the 'build.gradle' file"
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
        // Delete the 'build.gradle' from the module A to be recreated
        File buildFileB = new File(moduleALocation, "build.gradle")
        if (buildFileB.exists()) {
            buildFileB.delete()
        }

        when: "The user runs the 'createModuleBuild' task with the command line parameter '-Ppkg=all'"
        def resultCreateModuleBuild = runTask(":createModuleBuild","-P${PublicationUtils.MODULE_NAME_PROP}=${ModuleBuildTemplateLoader.ALL_COMMAND_PROPERTY}", "-P${PublicationUtils.REPOSITORY_NAME_PROP}=${REPOSITORY}")

        then: "The task will finish successfully"
        assert resultCreateModuleBuild.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "The build.gradle files will be created"
        File moduleALocationAfterCreation = new File(testProjectDir, "modules/${moduleA}")
        File buildFileAfterCreationModuleA = new File(moduleALocationAfterCreation, "build.gradle")
        assert buildFileAfterCreationModuleA.exists()

        File moduleBLocationAfterCreation = new File(testProjectDir, "modules/${moduleB}")
        File buildFileAfterCreationModuleB = new File(moduleBLocationAfterCreation, "build.gradle")
        assert buildFileAfterCreationModuleB.exists()

        and: "The build file from the module B should contain the dependency to A (declared in the AD_MODULE_DEPENDENCY.xml)"

        String buildFileBText = buildFileAfterCreationModuleB.text
        assert buildFileBText.contains("com.test:moduleApublish:1.0.0")
    }
}
