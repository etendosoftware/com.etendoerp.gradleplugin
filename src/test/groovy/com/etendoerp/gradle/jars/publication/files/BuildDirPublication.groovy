package com.etendoerp.gradle.jars.publication.files

import com.etendoerp.gradle.jars.publication.PublicationUtils
import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.TempDir
import spock.lang.Title

@Title("Test case to verify that the published module contains the 'build/classes' dir")
@Issue("EPL-295")
class BuildDirPublication extends EtendoSpecification {
    static final String ENVIRONMENTS_LOCATION = "src/test/resources/jars/multiplepublication/updatepublication/modules"
    static final String REPOSITORY = "etendo-multiplepublish-test"
    static final String REPOSITORY_URL = "https://repo.futit.cloud/repository/"

    @TempDir
    File testProjectDir

    @Override
    File getProjectDir() {
        System.out.println("🦜 "+ testProjectDir.getAbsolutePath())
        return testProjectDir
    }

    // Clean the repository
    def cleanupSpec() {
        PublicationUtils.cleanRepositoryModules(REPOSITORY)
    }

    def "Publishing a module with build/classes files"() {
        given: "The user publishing a version of a module with build/classes dir"
        def modulevalidations = "com.test.modulevalidations"
        def modulesDir = new File(testProjectDir, "modules")
        modulesDir.mkdirs()
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${modulevalidations}"), modulesDir)

        when: "The user publish the version of the module"
        def publishAmoduleTask = runTask("publishVersion", "-Ppkg=$modulevalidations")
        assert publishAmoduleTask.task(":publishVersion").outcome == TaskOutcome.SUCCESS

        and: "The modules are in the nexus repositories"
        Map<String, List<String>> modulesData = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        modulesData.put(modulevalidations, ["1.0.0"])
        PublicationUtils.correctModuleVersion(REPOSITORY, modulesData)

        then: "The published module should contain the 'build/classes' dir"
        // Validate ZIP file
        String  zipAddress = "${REPOSITORY_URL}${REPOSITORY}/com/test/modulevalidations/1.0.0/modulevalidations-1.0.0.zip"
        PublicationUtils.downloadAndValidateBuildDirFile(zipAddress, testProjectDir,"modulevalidations.zip")

        // Validate JAR file
        String  jarAddress = "${REPOSITORY_URL}${REPOSITORY}/com/test/modulevalidations/1.0.0/modulevalidations-1.0.0.jar"
        PublicationUtils.downloadAndValidateBuildDirFile(jarAddress, testProjectDir,"modulevalidations.jar")

    }
}
