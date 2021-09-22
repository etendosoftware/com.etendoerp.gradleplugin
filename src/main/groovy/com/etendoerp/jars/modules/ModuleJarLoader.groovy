package com.etendoerp.jars.modules

import com.etendoerp.jars.modules.mavenpublication.MavenPublicationLoader
import org.gradle.api.Project

class ModuleJarLoader {

    static load(Project project) {
        MavenPublicationLoader.load(project)
        ModuleJarGenerator.load(project)
        ModuleJarPublication.load(project)
    }

}
