package com.etendoerp.gradle.jars.resolution.webcontentexclusion

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.TempDir
import spock.lang.Title

@Title("Removing old version of a module in the WebContent directory")
@Issue("EPL-115")
class ModuleRemoveOldVersionInsideWebContentTest extends EtendoCoreResolutionSpecificationTest{

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreRepo() {
        return SNAPSHOT_REPOSITORY_URL
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "Removing old version of a Module when updates version" () {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFileFirst(getCoreRepo())

        String moduleToUpdate = "moduleToUpdate"
        String oldModuleVersion = "1.0.0"
        String newModuleVersion = "1.0.1"

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The users adds a module dependency in JARs"
        addRepositoryToBuildFile("https://repo.futit.cloud/repository/etendo-test")
        buildFile << """
        dependencies {
            implementation('com.test:${moduleToUpdate}:${oldModuleVersion}')  
        }
        """

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users install the environment"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "After running the 'smartbuild' task, the MODULE JAR file will be deployed in the WebContent dir"
        def smartbuildResult = runTask("smartbuild")
        smartbuildResult.task(":smartbuild").outcome  == TaskOutcome.SUCCESS

        def webContentLibs = new File(testProjectDir, "WebContent/WEB-INF/lib")
        def containsModuleOldVersion = false
        def containsModuleNewVersion = false

        webContentLibs.listFiles().each {
            if (it.name.contains("${moduleToUpdate}-${oldModuleVersion}")) {
                containsModuleOldVersion = true
            }
            if (it.name.contains("${moduleToUpdate}-${newModuleVersion}")) {
                containsModuleNewVersion = true
            }
        }

        // The WebContent should ONLY contain the OLD version
        assert containsModuleOldVersion
        assert !containsModuleNewVersion

        when: "The users updates the MODULE with a new version "
        buildFile << """
        dependencies {
            implementation('com.test:${moduleToUpdate}:${newModuleVersion}')  
        }
        """

        and: "The users runs the smartbuild task"
        def reSmartbuildResult = runTask("smartbuild")
        reSmartbuildResult.task(":smartbuild").outcome  == TaskOutcome.SUCCESS

        then: "The MODULE dependency will be updated in the WebContent, the old one should be deleted"
        def _webContentLibs = new File(testProjectDir, "WebContent/WEB-INF/lib")
        def _containsModuleOldVersion = false
        def _containsModuleNewVersion = false

        _webContentLibs.listFiles().each {
            if (it.name.contains("${moduleToUpdate}-${oldModuleVersion}")) {
                _containsModuleOldVersion = true
            }
            if (it.name.contains("${moduleToUpdate}-${newModuleVersion}")) {
                _containsModuleNewVersion = true
            }
        }

        // The WebContent should ONLY contain the NEW version
        assert !_containsModuleOldVersion
        assert _containsModuleNewVersion

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

}
