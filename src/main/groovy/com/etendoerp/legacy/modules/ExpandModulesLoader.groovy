package com.etendoerp.legacy.modules

import com.etendoerp.legacy.modules.expand.ExpandCustomModule
import com.etendoerp.legacy.modules.expand.ExpandModules
import org.gradle.api.Project

class ExpandModulesLoader {

    static void load(Project project) {
        ExpandCustomModule.load(project)
        ExpandModules.load(project)
    }
}
