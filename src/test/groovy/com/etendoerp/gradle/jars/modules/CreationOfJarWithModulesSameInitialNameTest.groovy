package com.etendoerp.gradle.jars.modules

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to verify that the creation of a JAR from a module is not including additional '.class' files from other module with the same initial name")
class CreationOfJarWithModulesSameInitialNameTest extends ModuleToJarSpecificationTest{
    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Issue("ERP-588")
    def "Creation of a JAR from a module with other module having the same initial name"() {
        given: "A Etendo project with the module '#moduleToJar' to be converted to JAR "
        def module = moduleToJar

        and: "The module contains java classes '#classesModuleJar' in the 'src' folder to be compiled"
        def srcLocation = "${testProjectDir.absolutePath}/modules/$module/src/${moduleToPath(module)}"
        createJavaFiles([module: module, location: srcLocation, javaClasses: classesModuleJar])

        and: "The module contains the 'AD_MODULE' file"
        def moduleLocation = "${testProjectDir.absolutePath}/modules/$module/"
        ModuleToJarUtils.createADModuleFile([baseLocation: moduleLocation, moduleProperties: moduleToJarProperties])

        and: "The users runs the 'createModuleBuild' task previous to the publication of the module"
        def moduleBuildResult = runTask(":createModuleBuild","-Ppkg=${module}", "-Prepo=${repository}") as BuildResult
        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "There is a extra module containing the same initial name"
        def extra = extraModule

        and: "The module contains java classes '#classesExtraModule' in the 'src' folder to be compiled"
        def extraSrcLocation = "${testProjectDir.absolutePath}/modules/$extraModule/src/${moduleToPath(extraModule)}"
        createJavaFiles([module: extra, location: extraSrcLocation, javaClasses: classesExtraModule])

        and: "The users runs the 'generate.entities' task"
        def entitiesResult = runTask(":generate.entities") as BuildResult
        entitiesResult.task(":generate.entities").outcome == TaskOutcome.SUCCESS

        and: "The corresponding '.class' files from the module '#moduleToJar' are in the 'build/classes' directory"
        validateClassFiles(module, classesModuleJar, [])

        and: "The corresponding '.class' files from the module '#extraModule' are in the 'build/classes' directory"
        validateClassFiles(extraModule, classesExtraModule, [])

        when: "The jar task is ran in the module '#moduleToJar'"
        def jarResult = runTask(":modules:$module:jar", "-Ppkg=$module") as BuildResult

        then: "The task will complete successfully."
        jarResult.task(":modules:$module:jar").outcome == TaskOutcome.SUCCESS

        and: "The jar file will be created in the /build/libs of the module folder."
        def jarFile = new File("${testProjectDir.absolutePath}/modules/$module/build/libs/$module-${moduleToJarProperties.version}.jar")
        assert jarFile.exists()

        and: "The jar file will only contain the 'classes' from the module '#moduleToJar'"
        def srcClassFiles = getListOfClasses(module, classesModuleJar, [])
        containsClassFiles(jarFile, srcClassFiles)

        where:
        moduleToJarProperties                                                               | repository    | moduleToJar        | extraModule              | classesModuleJar   | classesExtraModule
        [javapackage: "com.test.module1", version: "1.0.0", description: "com.test.module1"]| "etendo-test" | "com.test.module1" | "com.test.module1.extra" | ["Module1Class"]   | ["ExtraModule1Class"]
    }

}
