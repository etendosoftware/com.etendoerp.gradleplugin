package com.etendoerp.jars

import org.gradle.api.Project

class JarLoader {

    static load(Project project) {
        JarCoreGenerator.load(project)
    }


}
