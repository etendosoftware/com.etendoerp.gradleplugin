package com.etendoerp.gradle.jars.publication.multiplepublication

import com.etendoerp.gradle.jars.publication.PublicationUtils
import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Issue("EM-54")
@Title("Publish a module updating the parent dependencies")
@Narrative("""
   The users wants to publish a new version of a module,
   all other modules which have a dependency with the one being published,
   should be also publish with the correct version
""")
class MultiplePublicationTest extends EtendoSpecification {

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

    def "Running the publishVersion task with the recursive flag"() {
        given: "The user publishing a version of a module A:1.0.0"
        def moduleA = "com.test.moduleApublish"
        def modulesDir = new File(testProjectDir, "modules")
        modulesDir.mkdirs()
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleA}"), modulesDir)

        def publishAmoduleTask = runTask("publishVersion", "-Ppkg=$moduleA")
        assert publishAmoduleTask.task(":publishVersion").outcome == TaskOutcome.SUCCESS

        and: "The users creates a new module B:1.0.0 which depends on A:1.0.0"
        def moduleB = "com.test.moduleBpublish"
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleB}"), modulesDir)

        and: "The users publish the module B:1.0.0"
        def publishBmoduleTask = runTask("publishVersion", "-Ppkg=$moduleB")
        assert publishBmoduleTask.task(":publishVersion").outcome == TaskOutcome.SUCCESS

        and: "The modules are in the nexus repositories"
        Map<String, List<String>> modulesData = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        modulesData.put(moduleA, ["1.0.0"])
        modulesData.put(moduleB, ["1.0.0"])
        PublicationUtils.correctModuleVersion(REPOSITORY, modulesData)

        when: "The users wants to publish an updated version of the module A:1.0.1 with the recursive flag"
        def moduleAlocation = new File(testProjectDir, "modules/${moduleA}")

        // Change AD_MODULE
        def adModuleLocation = new File(moduleAlocation, "src-db/database/sourcedata/AD_MODULE.xml")
        assert adModuleLocation.exists()
        adModuleLocation.text = adModuleLocation.text.replace("1.0.0", "1.0.1")

        // Change build.gradle
        def buildGradleLocation = new File(moduleAlocation, "build.gradle")
        assert buildGradleLocation.exists()
        buildGradleLocation.text = buildGradleLocation.text.replace("1.0.0", "1.0.1")

        // Republish the module
        def republishAmoduleTask = runTask("publishVersion", "-Ppkg=$moduleA", "-Precursive=true")
        assert republishAmoduleTask.task(":publishVersion").outcome == TaskOutcome.SUCCESS

        then: "The modules A and B should be published, A:1.0.1 (by the user) and B:1.0.1 (automatic)"
        Map<String, List<String>> republishModuleData = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        def modA = republishModuleData.put(moduleA, ["1.0.0", "1.0.1"])
        def modB = republishModuleData.put(moduleB, ["1.0.0", "1.0.1"])
        PublicationUtils.correctModuleVersion(REPOSITORY, republishModuleData)

        and: "The automatic PUBLISHED contents of the module B should contain the correct version."
        def updatedModuleVersion = "1.0.1"

        // Validate POM file
        def pomAddress = "${REPOSITORY_URL}${REPOSITORY}/com/test/moduleBpublish/1.0.1/moduleBpublish-1.0.1.pom"
        Map pomDependencies = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        def pomA = pomDependencies.put(moduleA, ["group":"com.test", "artifact": "moduleApublish", "version": "1.0.1"])
        PublicationUtils.downloadAndValidatePomFile(pomAddress, testProjectDir, "moduleBpublish-1.0.1.pom", updatedModuleVersion, pomDependencies)

        // Validate ZIP file - 'build.gradle' and 'AD_MODULE.xml' files.
        String  zipAddress = "${REPOSITORY_URL}${REPOSITORY}/com/test/moduleBpublish/1.0.1/moduleBpublish-1.0.1.zip"
        PublicationUtils.downloadAndValidateZipFile(zipAddress, testProjectDir,"moduleBpublish.zip", updatedModuleVersion, pomDependencies)

        // Validate JAR file - 'AD_MODULE.xml' file.
        String  jarAddress = "${REPOSITORY_URL}${REPOSITORY}/com/test/moduleBpublish/1.0.1/moduleBpublish-1.0.1.jar"
        PublicationUtils.downloadAndValidateJarFile(jarAddress, testProjectDir,"moduleBpublish.jar", updatedModuleVersion)

        and: "The SOURCE files of the B module should be updated"
        def modulesLocation = new File(testProjectDir, "modules")
        def moduleBlocation = new File(modulesLocation, moduleB)

        def buildFile = new File(moduleBlocation, "build.gradle")
        PublicationUtils.validateBuildGradleFile(buildFile, updatedModuleVersion, pomDependencies)

        File adModuleFile = new File(moduleBlocation, "src-db/database/sourcedata/AD_MODULE.xml")
        PublicationUtils.validateAdModuleFile(adModuleFile, updatedModuleVersion)
    }
}
