package com.etendoerp

import com.etendoerp.jars.JarLoader
import com.etendoerp.legacy.EtendoLegacy
import com.etendoerp.modules.ModulesConfigurationLoader
import com.etendoerp.publication.PublicationLoader
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin

class EtendoPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def extension = project.extensions.create('etendo', EtendoPluginExtension)
        project.getPluginManager().apply(JavaBasePlugin.class)
        project.getPluginManager().apply(PublishingPlugin.class)
        project.getPluginManager().apply(MavenPublishPlugin.class)
        project.getPluginManager().apply(WarPlugin.class)

        project.java {
            withSourcesJar()
        }

        EtendoLegacy.load(project)
        JarLoader.load(project)
        PublicationLoader.load(project)
        ModulesConfigurationLoader.load(project)
    }
}
