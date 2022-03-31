package com.etendoerp.legacy.modules

import org.gradle.api.Project

class ModuleZipLoader {

    static void load(Project project) {
        ModuleZipGenerator.load(project)
    }

}
