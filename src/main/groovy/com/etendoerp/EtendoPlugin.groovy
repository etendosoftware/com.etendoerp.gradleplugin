package com.etendoerp

import com.etendoerp.copilot.CopilotLoader
import com.etendoerp.css.CssCompileLoader
import com.etendoerp.dbdeps.DepsLoader
import com.etendoerp.dependencies.DependenciesLoader
import com.etendoerp.dependencymanager.DependencyManagerLoader
import com.etendoerp.externaltasks.ExternalTasksLoader
import com.etendoerp.jars.JarLoader
import com.etendoerp.java.JavaCheckLoader
import com.etendoerp.legacy.EtendoLegacy
import com.etendoerp.legacy.ant.AntLoader
import com.etendoerp.modules.ModulesConfigurationLoader
import com.etendoerp.modules.uninstall.UninstallModuleLoader
import com.etendoerp.publication.PublicationLoader
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import com.etendoerp.publication.git.CloneDependencies

class EtendoPlugin implements Plugin<Project> {

    final static String PLUGIN_VERSION = "2.0.2"

    @Override
    void apply(Project project) {

        System.out.println("**********************************************")
        System.out.println("* ETENDO PLUGIN VERSION: ${PLUGIN_VERSION}")
        System.out.println("**********************************************")

        def extension = project.extensions.create('etendo', EtendoPluginExtension)
        project.getPluginManager().apply(JavaBasePlugin.class)
        project.getPluginManager().apply(PublishingPlugin.class)
        project.getPluginManager().apply(MavenPublishPlugin.class)
        project.getPluginManager().apply(WarPlugin.class)

        project.java {
            withSourcesJar()
        }

        JavaCheckLoader.load(project)
        AntLoader.load(project)
        EtendoLegacy.load(project)
        DepsLoader.load(project)
        JarLoader.load(project)
        PublicationLoader.load(project)
        ModulesConfigurationLoader.load(project)
        DependenciesLoader.load(project)
        CloneDependencies.load(project)
        CssCompileLoader.load(project)
        UninstallModuleLoader.load(project)
        ExternalTasksLoader.load(project)
        CopilotLoader.load(project)
        DependencyManagerLoader.load(project)
    }
}
