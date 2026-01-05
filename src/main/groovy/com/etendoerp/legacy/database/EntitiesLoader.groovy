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

        // CRITICAL FIX: Copy .template files without the extension to build/classes
        // This is needed because GenerateEntitiesTask creates META-INF/services/*.template files
        // but Hibernate needs them without the .template extension in the classpath
        task.doLast {
            project.logger.lifecycle("Processing META-INF services templates...")
            def srcGenDir = project.file('src-gen')
            def buildClassesDir = project.file('build/classes')

            if (srcGenDir.exists()) {
                def servicesDir = new File(srcGenDir, 'META-INF/services')
                if (servicesDir.exists()) {
                    def templateFiles = servicesDir.listFiles().findAll { it.name.endsWith('.template') }
                    if (templateFiles) {
                        def destServicesDir = new File(buildClassesDir, 'META-INF/services')
                        destServicesDir.mkdirs()

                        templateFiles.each { templateFile ->
                            def targetName = templateFile.name.replaceAll('\\.template$', '')
                            def targetFile = new File(destServicesDir, targetName)
                            project.logger.lifecycle("Copying ${templateFile.name} -> ${targetName}")
                            project.copy {
                                from templateFile
                                into destServicesDir
                                rename { targetName }
                            }
                        }
                    }
                }
            }
        }
    }
}
