package com.etendoerp.gradle.jars.dependencies

import com.etendoerp.gradle.jars.JarsUtils
import com.etendoerp.gradle.jars.modules.ModuleToJarSpecificationTest
import com.etendoerp.gradle.jars.modules.ModuleToJarUtils
import com.etendoerp.gradle.tests.EtendoSpecification
import com.etendoerp.jars.PathUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir

class ModuleWithMavenLibraryDependencyTest extends EtendoSpecification{

    static String BASE_MODULE = PublicationUtils.BASE_MODULE_DIR
    static String REPO = PublicationUtils.REPOSITORY_NAME_PROP
    static String PKG  = PublicationUtils.MODULE_NAME_PROP

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def setup() {
        def buildXml = new File("${getProjectDir().absolutePath}/build.xml")
        buildXml.text = JarsUtils.dummyBuildXml()
    }

    def "Adding a dependency to the build gradle file and using it in a module compiles successfully"() {
        given: "A module to be convented to a gradle subproject"
        def module = moduleName

        and: "The module contains the 'AD_MODULE' file"
        def moduleLocation = "${testProjectDir.absolutePath}/modules/$module/"
        ModuleToJarUtils.createADModuleFile([baseLocation: moduleLocation, moduleProperties: moduleProperties])

        and: "The users runs the 'createModuleBuild' task"
        def moduleBuildResult = runTask(":createModuleBuild","-P${PKG}=${module}", "-P${REPO}=${repository}") as BuildResult

        and: "The task will finish successfully"
        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "The 'build.gradle' file will be created in the module location"
        def buildFile = new File("${testProjectDir.absolutePath}/modules/${module}/build.gradle")
        assert buildFile.exists()

        and: "The users set a dependency in the 'build.gradle' file of the module"
        buildFile << JarsUtils.generateDependenciesBlock(dependencies)

        and: "The users creates a java class to use the dependency"
        def location = "${testProjectDir.absolutePath}/modules/$module/src/${PathUtils.fromModuleToPath(module)}"
        ModuleToJarSpecificationTest.createJavaFiles([location: location, module: module, javaClasses: javaClasses, imports: imports, methods: methods])

        when: "The users compiles the java classes"
        def compilationResult = runTask(":${BASE_MODULE}:${module}:compileJava")

        then: "The task will complete successfully"
        compilationResult.task(":${BASE_MODULE}:${module}:compileJava").outcome == TaskOutcome.SUCCESS

        and: "The '.class' files will be stored in the 'build/classes' directory"
        JarsUtils.validateClassFiles(getProjectDir().absolutePath, module, javaClasses, [])

        where:
        moduleProperties                                                                        | moduleName           | repository    | dependencies                       | imports                  | javaClasses     | methods
        [javapackage: "com.test.moduledep", version: "1.0.0", description: "com.test.moduledep"]| "com.test.moduledep" | "etendo-test" |["com.google.code.gson:gson:2.8.7"] | ["com.google.gson.Gson"] | ["CustomClass"] | ["""void test() { Gson gson = new Gson(); }"""]

    }

    def "Using a dependency not added in the build gradle file will fail"() {
        given: "A module to be convented to a gradle subproject"
        def module = moduleName

        and: "The module contains the 'AD_MODULE' file"
        def moduleLocation = "${testProjectDir.absolutePath}/modules/$module/"
        ModuleToJarUtils.createADModuleFile([baseLocation: moduleLocation, moduleProperties: moduleProperties])

        and: "The users runs the 'createModuleBuild' task"
        def moduleBuildResult = runTask(":createModuleBuild","-P${PKG}=${module}", "-P${REPO}=${repository}") as BuildResult

        and: "The task will finish successfully"
        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "The 'build.gradle' file will be created in the module location"
        def buildFile = new File("${testProjectDir.absolutePath}/modules/${module}/build.gradle")
        assert buildFile.exists()

        and: "The users creates a java class to use a unregistered java library"
        def location = "${testProjectDir.absolutePath}/modules/$module/src/${PathUtils.fromModuleToPath(module)}"
        ModuleToJarSpecificationTest.createJavaFiles([location: location, module: module, javaClasses: javaClasses, imports: imports, methods: methods])

        when: "The users compiles the java classes"
        def compilationResult = runTaskAndFail(":${BASE_MODULE}:${module}:compileJava")

        then: "The task will fail"
        compilationResult.task(":compileJava").outcome == TaskOutcome.FAILED

        where:
        moduleProperties                                                                        | moduleName           | repository    | imports                  | javaClasses     | methods
        [javapackage: "com.test.modulenodep", version: "1.0.0", description: "com.test.modulenodep"]| "com.test.modulenodep" | "etendo-test" | ["com.google.gson.Gson"] | ["CustomClass"] | ["""void test() { Gson gson = new Gson(); }"""]

    }

}
