package com.etendoerp

import com.etendoerp.jars.JarLoader;
import com.etendoerp.legacy.EtendoLegacy
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin

class EtendoPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def extension = project.extensions.create('etendo', EtendoPluginExtension)
        project.getPluginManager().apply(JavaPlugin.class)
        project.getPluginManager().apply(PublishingPlugin.class)
        project.getPluginManager().apply(MavenPublishPlugin.class)
        project.getPluginManager().apply(WarPlugin.class)

        EtendoLegacy.load(project)
        JarLoader.load(project)

    }
}
