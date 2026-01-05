package com.etendoerp.legacy.database

import com.etendoerp.legacy.ant.AntLoader
import org.gradle.api.Project
import org.gradle.api.tasks.ant.AntTarget

class EntitiesLoader {

    static void load(Project project) {
        project.afterEvaluate {
            def generateEntitiesTask = project.tasks.findByName('generate.entities')
            if (generateEntitiesTask != null) {
                configureEntitiesTask(project, generateEntitiesTask)
                
                def prepareConfigTask = project.tasks.findByName('prepareConfig')
                if (prepareConfigTask != null) {
                    generateEntitiesTask.dependsOn(prepareConfigTask)
                }
            }
            
            def compileJavaTask = project.tasks.findByName('compileJava')
            if (compileJavaTask != null && generateEntitiesTask != null) {
                compileJavaTask.dependsOn(generateEntitiesTask)
            }
        }
    }

    private static void configureEntitiesTask(Project project, def task) {
        // Detect mode
        def coreInSources = AntLoader.isCoreInSources(project)
        def corePath = coreInSources ? "." : "build/etendo"

        // --- INPUTS ---
        // 1. Core model
        task.inputs.files(project.fileTree("${corePath}/src-db/database/model") { include '**/*.xml' }).withPropertyName('coreModel')
        
        // 2. Modules model
        task.inputs.files(project.fileTree('modules') { include '**/src-db/database/model/**/*.xml' }).withPropertyName('modulesModel')
        
        if (project.file('modules_core').exists()) {
            task.inputs.files(project.fileTree('modules_core') { include '**/src-db/database/model/**/*.xml' }).withPropertyName('modulesCoreModel')
        }

        // 3. Config
        task.inputs.files(project.tasks.named('prepareConfig')).withPropertyName('config')

        // --- OUTPUTS ---
        task.outputs.dir('src-gen').withPropertyName('generatedEntities')
        
        // Enable caching
        task.outputs.cacheIf { true }
    }
}
