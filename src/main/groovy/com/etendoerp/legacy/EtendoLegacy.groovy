package com.etendoerp.legacy

import com.etendoerp.legacy.modules.ModuleZipLoader;
import org.gradle.api.Project

class EtendoLegacy {

    static void load(Project project) {

        LegacyScriptLoader.load(project)
        ModuleZipLoader.load(project)

    }
}
