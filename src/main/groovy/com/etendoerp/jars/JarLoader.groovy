package com.etendoerp.jars

import com.etendoerp.jars.modules.ModuleJarLoader
import org.gradle.api.Project

class JarLoader {

    static load(Project project) {
        ExtractResourcesOfJars.load(project)
        JarCoreGenerator.load(project)
        ModuleJarLoader.load(project)
    }
}