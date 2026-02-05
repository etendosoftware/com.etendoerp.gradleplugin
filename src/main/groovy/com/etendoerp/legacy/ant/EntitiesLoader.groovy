package com.etendoerp.legacy.ant

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec

/**
 * EntitiesLoader - Configuración de generación de entidades Hibernate
 * 
 * Equivalente Ant: generate.entities, generate.entities.quick
 * Clase Java: org.openbravo.base.gen.GenerateEntitiesTask
 * 
 * Esta tarea genera las clases de entidad Hibernate desde el modelo de datos
 */
class EntitiesLoader {
    
    static final String ENTITIES_CLASS = 'org.openbravo.base.gen.GenerateEntitiesTask'
    
    static void load(Project project) {
        
        /**
         * Task: etendoGenerateEntities
         * Genera entidades Hibernate desde el modelo de datos
         */
        project.tasks.register('etendoGenerateEntities', JavaExec) {
            group = 'etendo build'
            description = 'Generate Hibernate entities from data model'
            
            mainClass.set(ENTITIES_CLASS)
            
            maxHeapSize = '1024m'
            jvmArgs = ['-Djava.security.egd=file:///dev/urandom']
            
            doFirst {
                // El classpath debe incluir las clases compiladas + dependencias
                classpath = project.configurations.runtimeClasspath + 
                           project.files("${project.buildDir}/classes")
                
                def configPath = "${project.projectDir}/config/Openbravo.properties"
                
                // Verificar que existe el archivo de configuración
                if (!project.file(configPath).exists()) {
                    throw new org.gradle.api.GradleException(
                        "Openbravo.properties not found. Run setup first."
                    )
                }
                
                args = [configPath]
                
                project.logger.info("* Generating Hibernate entities")
                project.logger.info("  Config: ${configPath}")
            }
            
            doLast {
                project.logger.lifecycle("* Entity generation completed")
            }
        }
        
        /**
         * Task: etendoGenerateEntitiesQuick
         * Versión incremental de generación de entidades
         * Solo regenera si hay cambios en el modelo
         */
        project.tasks.register('etendoGenerateEntitiesQuick', JavaExec) {
            group = 'etendo build'
            description = 'Generate Hibernate entities (incremental)'
            
            mainClass.set(ENTITIES_CLASS)
            
            maxHeapSize = '1024m'
            jvmArgs = ['-Djava.security.egd=file:///dev/urandom']
            
            // Marcar como up-to-date si src-gen no ha cambiado
            outputs.upToDateWhen {
                def srcGen = project.file('src-gen')
                if (!srcGen.exists()) return false
                
                // Verificar si hay archivos más nuevos que los generados
                def lastModified = 0L
                srcGen.eachFileRecurse { file ->
                    if (file.lastModified() > lastModified) {
                        lastModified = file.lastModified()
                    }
                }
                
                // Si el último archivo fue modificado hace más de 5 minutos, skip
                return (System.currentTimeMillis() - lastModified) > 300000
            }
            
            doFirst {
                classpath = project.configurations.runtimeClasspath + 
                           project.files("${project.buildDir}/classes")
                
                def configPath = "${project.projectDir}/config/Openbravo.properties"
                args = [configPath]
                
                project.logger.info("* Generating Hibernate entities (quick mode)")
            }
        }
        
        // Configurar las tareas Ant existentes si están disponibles
        project.afterEvaluate {
            configureAntEntitiesTasks(project)
        }
    }
    
    /**
     * Configura las propiedades necesarias para las tareas Ant de entidades
     */
    private static void configureAntEntitiesTasks(Project project) {
        // Buscar la tarea Ant de entidades
        def genEntities = project.tasks.findByName('generate.entities')
        def genEntitiesQuick = project.tasks.findByName('generate.entities.quick')
        
        if (genEntities || genEntitiesQuick) {
            // Configurar propiedades Ant necesarias
            project.ant.properties['base.config'] = "${project.projectDir}/config"
            project.ant.properties['build'] = "${project.buildDir}/classes"
            project.ant.properties['src.gen'] = "${project.projectDir}/src-gen"
            
            // Asegurar que las entidades se generen después de compilar
            if (genEntities) {
                def compileJava = project.tasks.findByName('compileJava')
                if (compileJava) {
                    genEntities.dependsOn(compileJava)
                }
            }
            
            if (genEntitiesQuick) {
                def compileJava = project.tasks.findByName('compileJava')
                if (compileJava) {
                    genEntitiesQuick.dependsOn(compileJava)
                }
            }
        }
    }
}
