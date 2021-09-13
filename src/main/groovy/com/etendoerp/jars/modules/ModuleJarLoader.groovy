package com.etendoerp.jars.modules

import org.gradle.api.Project

class ModuleJarLoader {

    static final String MODULE_NAME_PROP = "mod"

    static load(Project project) {
        ModuleJarGenerator.load(project)
        ModuleJarPublication.load(project)
    }

}
