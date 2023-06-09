package com.etendoerp.legacy

import com.etendoerp.legacy.dependencies.ResolverDependencyLoader
import com.etendoerp.legacy.modules.ExpandModulesLoader
import com.etendoerp.legacy.modules.ModuleZipLoader;
import org.gradle.api.Project

class EtendoLegacy {

    static void load(Project project) {
        ExpandModulesLoader.load(project)
        LegacyScriptLoader.load(project)
        ModuleZipLoader.load(project)
        ResolverDependencyLoader.load(project)
    }
}
