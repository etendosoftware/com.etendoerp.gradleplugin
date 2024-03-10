package com.etendoerp.legacy.ant.compilejava

import org.gradle.api.Project

class CompileJavaLoader {

    static final String TASK_NAME = 'compileJavaDummy'

    static void load(Project project) {
        project.tasks.register(TASK_NAME, CompileJavaDummy) {
            doLast {
                project.logger.debug('* compileJavaDummy task executed.')
            }
        }
    }

}