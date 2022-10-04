package com.etendoerp.gradle.jars.dependencies

import com.etendoerp.gradle.jars.modules.ModuleToJarSpecificationTest
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.jars.PathUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir
import com.etendoerp.gradle.jars.JarsUtils
import spock.lang.Title

@Title("Test to verify the correct import of the Etendo core jar")
class ProjectWithEtendoCoreDependency extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    def "Adding the Etendo core dependency to a project"() {
        given: "A module which will use the Etendo core classes"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution : true]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        def module = moduleName

        and: "The users creates a java class to use the dependency"
        def location = "${testProjectDir.absolutePath}/modules/$module/src/${PathUtils.fromModuleToPath(module)}"
        ModuleToJarSpecificationTest.createJavaFiles([location: location, module: module, javaClasses: javaClasses, imports: imports, methods: methods])

        when: "The users compiles the java classes"
        def compilationResult = runTask(":compileJava")

        then: "The task will complete successfully"
        compilationResult.task(":compileJava").outcome == TaskOutcome.SUCCESS

        and: "The '.class' files will be stored in the 'build/classes' directory"
        JarsUtils.validateClassFiles(getProjectDir().absolutePath, module, javaClasses, [])

        where:
        moduleName           | repository    | imports                                            | javaClasses      | methods
        "com.test.moduledep" | "etendo-test" | ["org.openbravo.erpCommon.utility.OBError"] | ["CustomClass"]  | ["""void test() { OBError err = new OBError(); }"""]
    }

}
