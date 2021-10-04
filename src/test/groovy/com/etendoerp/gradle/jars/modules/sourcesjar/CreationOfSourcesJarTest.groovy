package com.etendoerp.gradle.jars.modules.sourcesjar

import com.etendoerp.gradle.jars.modules.ModuleToJarSpecificationTest
import com.etendoerp.gradle.jars.modules.ModuleToJarUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared
import spock.lang.TempDir

class CreationOfSourcesJarTest extends ModuleToJarSpecificationTest {
    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def "Creation of the sources JAR of a module" () {
        given: "A Etendo project with the module '#moduleToJar' to be converted to sources JAR "
        def module = moduleToJar

        and: "The module contains java classes '#classesModuleJar' in the 'src' folder"
        def srcLocation = "${testProjectDir.absolutePath}/modules/$module/src"
        createJavaFilesWithPackage([baseLocation: srcLocation, module: module, packages: packages, javaClasses: classesModuleJar])

        and: "The module contains the 'AD_MODULE' file"
        def moduleLocation = "${testProjectDir.absolutePath}/modules/$module/"
        ModuleToJarUtils.createADModuleFile([baseLocation: moduleLocation, moduleProperties: moduleToJarProperties])

        and: "The users runs the 'createModuleBuild' task previous to the publication of the module"
        def moduleBuildResult = runTask(":createModuleBuild","-Ppkg=${module}", "-Prepo=${repository}") as BuildResult
        moduleBuildResult.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "The users runs the 'generate.entities' task"
        def entitiesResult = runTask(":generate.entities") as BuildResult
        entitiesResult.task(":generate.entities").outcome == TaskOutcome.SUCCESS

        when: "The sources jar task is ran in the module '#moduleToJar'"
        def jarResult = runTask(":modules:$module:sourcesJar", "-Ppkg=$module") as BuildResult

        then: "The task will complete successfully."
        jarResult.task(":modules:$module:sourcesJar").outcome == TaskOutcome.SUCCESS

        and: "The sources jar file will be created in the /build/libs of the module folder."
        def sourcesJarFile = new File("${testProjectDir.absolutePath}/modules/$module/build/libs/$module-${moduleToJarProperties.version}-sources.jar")
        assert sourcesJarFile.exists()

        and: "The jar file will only contain the java classes files (.java) from the module '#moduleToJar'"
        def srcJavaFiles = getListOfJavaFilesWithPackage([module: module, javaFiles: classesModuleJar, packages: packages])
        containsJavaFiles(sourcesJarFile, srcJavaFiles)

        where:
        moduleToJarProperties                                                                                 | repository    | moduleToJar                 | classesModuleJar                | packages
        [javapackage: "com.test.modulesourcesjar", version: "1.0.0", description: "com.test.modulesourcesjar"]| "etendo-test" | "com.test.modulesourcesjar" | ["Module1Class","Module2Class"] | ["", "extrapackage"]

    }

}
