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
import spock.lang.Title

@Title("Test to check that a modules compiles when imports another module classes.")
class ModuleWithOtherModuleDependencyTest extends EtendoSpecification {

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

    def "A module uses the classes from another module"() {
        given: "A extra module"
        def extraModule = extraModuleName

        and: "The extra module contains defined classes"
        def extraLocation = "${testProjectDir.absolutePath}/modules/$extraModule/src/${PathUtils.fromModuleToPath(extraModule)}"
        ModuleToJarSpecificationTest.createJavaFiles([location: extraLocation, module: extraModule, javaClasses: javaClassesExtra])

        and: "The extra module creates the build.gradle file"
        createModuleBuild(extraModule, extraModuleProperties, repository)

        and: "There is another module which will import the extra module #extraModuleName"
        def module = moduleName

        and: "The module will contains classes importing the extra module"
        def location = "${testProjectDir.absolutePath}/modules/$module/src/${PathUtils.fromModuleToPath(module)}"
        ModuleToJarSpecificationTest.createJavaFiles([location: location, module: module, javaClasses: javaClasses, imports: imports, methods: methodsModule])

        and: "The module creates the build.gradle file"
        createModuleBuild(module, moduleProperties, repository)

        when: "The users compiles the java classes"
        def compilationResult = runTask(":${BASE_MODULE}:${module}:compileJava")

        then: "The task will complete successfully"
        compilationResult.task(":${BASE_MODULE}:${module}:compileJava").outcome == TaskOutcome.SUCCESS

        and: "The '.class' files will be stored in the 'build/classes' directory"
        JarsUtils.validateClassFiles(getProjectDir().absolutePath, module, javaClasses, [])


        where:
        moduleProperties                                                                     | moduleName         | repository    | javaClasses     | imports                  | methodsModule
        [javapackage: "com.test.module1", version: "1.0.0", description: "com.test.module1"] | "com.test.module1" | "etendo-test" | ["CustomClass"] | ["com.test.module2.*;"]  | [""" void test() { var x = new CustomExtraClass();} """]
        __
        extraModuleProperties                                                                | extraModuleName   | javaClassesExtra
        [javapackage: "com.test.module2", version: "1.0.0", description: "com.test.module2"] | "com.test.module2"| ["CustomExtraClass"]
    }

    void createModuleBuild(String moduleName, def moduleProperties, def repository) {
        def module = moduleName

        def moduleLocation = "${testProjectDir.absolutePath}/modules/$module/"
        ModuleToJarUtils.createADModuleFile([baseLocation: moduleLocation, moduleProperties: moduleProperties])

        def moduleBuildResult = runTask(":createModuleBuild","-P${PKG}=${module}", "-P${REPO}=${repository}") as BuildResult

        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        def buildFile = new File("${testProjectDir.absolutePath}/modules/${module}/build.gradle")
        assert buildFile.exists()
    }


}
