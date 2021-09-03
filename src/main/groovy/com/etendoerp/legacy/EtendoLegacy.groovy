package com.etendoerp.legacy;

import org.gradle.api.Project

class EtendoLegacy {

    static void load(Project project) {

        LegacyScriptLoader.load(project)

    }
}
