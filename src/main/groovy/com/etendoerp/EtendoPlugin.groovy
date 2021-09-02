package com.etendoerp;

import com.etendoerp.plugin.EtendoModule;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

class EtendoPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        EtendoModule.load(project);
    }
}
