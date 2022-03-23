package com.etendoerp.gradle.jars.publication.buildfile

import com.etendoerp.gradle.tests.EtendoSpecification
import com.etendoerp.legacy.utils.ModulesUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.buildfile.BuildFileUtils
import com.etendoerp.publication.buildfile.BuildMetadata
import com.etendoerp.publication.buildfile.ModuleBuildTemplateLoader
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Issue("EM-62")
@Title("Running the 'createModuleBuild' task updates the bundle build file")
@Narrative(""" 
    When the user runs the task 'createModuleBuild' with the -Pbundle property,
    the build.gradle file should be created with the correct dependencies.
""")
class BundleBuildFileCreationTest extends EtendoSpecification {
    static final String ENVIRONMENTS_LOCATION = "src/test/resources/jars/multiplepublication/updatepublication/modules"
    static final String REPOSITORY = "etendo-multiplepublish-test"
    static final String REPOSITORY_URL = "https://repo.futit.cloud/repository/"

    @TempDir
    File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "Running the createModuleBuild for a bundle"() {
        given: "A user wanting to update the gradle build file for a bundle"

        def modulesDir = new File(testProjectDir, "modules")
        modulesDir.mkdirs()

        def bundle = "com.etendoerp.localization.spain.extension"
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${bundle}"), modulesDir)
        File bundleLocation = new File(testProjectDir, "modules/${bundle}")

        File bundleBuildFile = new File(bundleLocation, "build.gradle")
        assert bundleBuildFile.exists()

        and: "The user obtains the source modules specified by the bundle in the 'modules-extension' list"

        def moduleADirName = "com.test.moduleApublish"
        def moduleAJavaPackage = "com.test.moduleApublish"

        def moduleBDirName = "com.test.moduleBpublish"
        def moduleBJavaPackage = "com.test.moduleBpublish"

        def moduleCDirName = "com.test.moduleCpublish"
        def moduleCJavaPackage = "com.test.moduleCpublish"

        def moduleDDirName = "com.test.moduleDpublish_eses"
        def moduleDJavaPackage = "com.test.moduleDpublish_esES"

        def moduleEDirName = "com.test.moduleEpublish_ese"
        def moduleEJavaPackage = "com.test.moduleEpublish_esES"

        List modules = [
                ["dirname": moduleADirName, "package": moduleAJavaPackage],
                ["dirname": moduleBDirName, "package": moduleBJavaPackage],
                ["dirname": moduleCDirName, "package": moduleCJavaPackage],
                ["dirname": moduleDDirName, "package": moduleDJavaPackage],
                ["dirname": moduleEDirName, "package": moduleEJavaPackage]
        ]

        copyDirectories(new File(ENVIRONMENTS_LOCATION), modulesDir, modules.collect({it.dirname}).toList())
        cleanBuildFiles(modulesDir, modules.collect({it.dirname}).toList())

        String pkgPropertyValue = runAll ? ModuleBuildTemplateLoader.ALL_COMMAND_PROPERTY : bundle

        when: "The users runs the createModuleBuild task passing the bundle"
        def creationResult = runTask(":createModuleBuild",
                "-P${PublicationUtils.MODULE_NAME_PROP}=${pkgPropertyValue}",
                "-P${BuildFileUtils.BUNDLE_PROPERTY}=${bundle}",
                "-P${PublicationUtils.REPOSITORY_NAME_PROP}=${REPOSITORY}",
        )

        then: "The task run successfully"
        assert creationResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "The build.gradle for the bundle is created"
        File bundleBuildFileUpdated = new File(testProjectDir, "modules/${bundle}/build.gradle")
        assert bundleBuildFileUpdated.exists()

        and: "The bundle contains the correct dependencies"
        checkDependenciesInBuildFile(bundleBuildFileUpdated, modules.collect({it.package}).toList())

        and: "The bundle continues having the list of repositories"
        assert bundleBuildFileUpdated.text.contains(BuildMetadata.APPLY_EXTENSION_FILE_VALUE)

        if (runAll) {
            modules.collect({it.dirname}).each {
                File location = new File(testProjectDir, "modules${File.separator}${it}${File.separator}build.gradle")
                assert location.exists()
            }
        }

        where:
        runAll | _
        false  | _
        true   | _
    }

    static void copyDirectories(File location, File destination, List filenames) {
        for (String file : filenames) {
            File fileDirLocation = new File(location, file)
            if (fileDirLocation.exists() && fileDirLocation.isDirectory()) {
                FileUtils.copyDirectoryToDirectory(fileDirLocation, destination)
            }
        }
    }

    static void cleanBuildFiles(File modulesLocation, List<String> modules) {
        for (String module : modules) {
            File buildModuleFile = new File(modulesLocation, "${module}${File.separator}build.gradle")
            if (buildModuleFile.exists()) {
                buildModuleFile.delete()
            }
        }
    }

    static void checkDependenciesInBuildFile(File buildFile, List<String> modules) {
        String buildFileText = buildFile.text
        for (String module : modules) {
            String group    = ModulesUtils.splitGroup(module)
            String artifact = ModulesUtils.splitArtifact(module)
            String dependency = "${group}:${artifact}"
            assert buildFileText.contains(dependency)
        }
    }
}
