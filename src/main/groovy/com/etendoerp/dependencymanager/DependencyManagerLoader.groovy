package com.etendoerp.dependencymanager

import com.etendoerp.dependencymanager.postupdate.InstallationStatusUpdate
import org.gradle.api.Project

class DependencyManagerLoader {
    static void load(Project project) {
        InstallationStatusUpdate.load(project)
    }
}
