package com.etendoerp.legacy.ant.compilejava

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Dummy class used to prevent deleting the 'build/classes' directory when
 * the 'compileJava' task is executed for first time.
 *
 * The 'OutputDirectory' points to 'build/classes'.
 *
 * The compilation tasks (smartbuild, compile.complete, etc) should depend on this task.
 *
 * This solves the problem of the gradle class 'CleanupStaleOutputsExecuter' cleaning
 * the directory
 *
 */
class CompileJavaDummy extends DefaultTask {

    @OutputDirectory
    def outputFile = project.objects.directoryProperty().fileValue(new File(project.buildDir,"classes"))

    @TaskAction
    void action() {
    }

}
