package com.etendoerp.jars.modules

import org.gradle.api.Project

class ModuleJarLoader {

    static load(Project project) {
        ModuleJarGenerator.load(project)
    }

}
