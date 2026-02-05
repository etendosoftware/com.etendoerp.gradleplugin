package com.etendoerp.legacy.ant

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec

/**
 * WadGenerateLoader - Migración de la tarea Ant 'wad' a Gradle
 * 
 * Ejecuta org.openbravo.wad.Wad para generar los formularios de la aplicación
 * 
 * La tarea WAD genera:
 * - Código Java para formularios
 * - Archivos XML de configuración
 * - Recursos FO para reportes
 */
class WadGenerateLoader {
    
    static final String WAD_CLASS = 'org.openbravo.wad.Wad'
    static final String WAD_BUILD_AD = 'build/javasqlc/srcAD'
    
    static void load(Project project) {
        
        /**
         * Task: etendoWad
         * Equivalente Ant: <java classname="org.openbravo.wad.Wad">
         * 
         * Genera formularios WAD desde la base de datos
         */
        project.tasks.register('etendoWad', JavaExec) {
            group = 'etendo build'
            description = 'Generate WAD UI components from database'
            
            mainClass.set(WAD_CLASS)
            classpath = project.configurations.runtimeClasspath
            
            maxHeapSize = '1024m'
            jvmArgs = ['-Djava.security.egd=file:///dev/urandom']
            
            doFirst {
                def coreInSources = AntLoader.isCoreInSources(project)
                def basePath = coreInSources 
                    ? project.projectDir.absolutePath 
                    : "${project.buildDir}/etendo"
                
                def configPath = "${project.projectDir}/config/Openbravo.properties"
                def buildADPath = project.file(WAD_BUILD_AD).absolutePath
                
                // Crear directorio de salida
                project.file(WAD_BUILD_AD).mkdirs()
                
                // Argumentos para Wad
                // Formato: propertiesFile obDir buildAD tabName [attachPath] [webURL]
                args = [
                    configPath,     // Archivo de propiedades
                    basePath,       // Directorio base de Openbravo
                    buildADPath,    // Directorio de salida para AD
                    'all'           // 'all' para generar todos los tabs
                ]
                
                project.logger.info("* Running WAD generation")
                project.logger.info("  Config: ${configPath}")
                project.logger.info("  Base: ${basePath}")
                project.logger.info("  Output: ${buildADPath}")
            }
            
            doLast {
                project.logger.lifecycle("* WAD generation completed")
            }
        }
        
        /**
         * Task: etendoWadQuick
         * Versión rápida de WAD que solo procesa cambios
         * Equivalente a wad.quick de Ant
         */
        project.tasks.register('etendoWadQuick', JavaExec) {
            group = 'etendo build'
            description = 'Generate WAD UI components (quick/incremental mode)'
            
            mainClass.set(WAD_CLASS)
            classpath = project.configurations.runtimeClasspath
            
            maxHeapSize = '1024m'
            jvmArgs = ['-Djava.security.egd=file:///dev/urandom']
            
            doFirst {
                def coreInSources = AntLoader.isCoreInSources(project)
                def basePath = coreInSources 
                    ? project.projectDir.absolutePath 
                    : "${project.buildDir}/etendo"
                
                def configPath = "${project.projectDir}/config/Openbravo.properties"
                def buildADPath = project.file(WAD_BUILD_AD).absolutePath
                
                project.file(WAD_BUILD_AD).mkdirs()
                
                // En modo quick, se pasa el tab específico o vacío para incremental
                args = [
                    configPath,
                    basePath,
                    buildADPath,
                    ''  // Vacío = modo incremental basado en checksums
                ]
                
                project.logger.info("* Running WAD generation (quick mode)")
            }
        }
        
        /**
         * Task: postwad
         * Copia archivos generados por WAD al directorio de diseño
         * Equivalente a postwad de Ant
         */
        project.tasks.register('postwad') {
            group = 'etendo build'
            description = 'Copy WAD generated files to design directory'
            
            doLast {
                def buildAD = project.file(WAD_BUILD_AD)
                def designDir = project.file("${project.buildDir}/classes/design")
                
                if (buildAD.exists()) {
                    project.copy {
                        from buildAD
                        into designDir
                        include '**/*.xml'
                        include '**/*.fo'
                        include '**/*.html'
                        include '**/*.srpt'
                        include '**/*.jrxml'
                        include '**/*.jasper'
                    }
                    project.logger.info("* Copied WAD artifacts to ${designDir}")
                }
            }
        }
    }
}
