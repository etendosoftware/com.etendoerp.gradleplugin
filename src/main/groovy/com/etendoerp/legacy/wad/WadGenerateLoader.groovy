package com.etendoerp.legacy.wad

import org.gradle.api.Project

class WadGenerateLoader {

    static void load(Project project) {
        registerTask(project)

        project.afterEvaluate {
            def wadTask = project.tasks.findByName('gradleWad')
            if (wadTask != null) {
                ['createQuartzProperties', 'createOBProperties', 'createBackupProperties'].each { genTaskName ->
                    def genTask = project.tasks.findByName(genTaskName)
                    if (genTask != null) {
                        wadTask.dependsOn(genTask)
                    }
                }
            }
        }
    }

    private static void registerTask(Project project) {
        project.tasks.register('gradleWad') {
            description = 'Generates UI windows and web.xml using WAD (Incremental)'
            group = 'etendo-wad'

            dependsOn 'gradleWadLib', 'gradleSqlc'

            // --- INPUTS: Lo que hace que WAD cambie ---
            // 1. Plantillas y lógica de WAD
            inputs.dir('src-wad/src').withPropertyName('wadTemplates')
            
            // 2. Diccionario de Aplicación (Modelos XML)
            inputs.files(project.fileTree(dir: 'src-db/database/model', include: '**/*.xml')).withPropertyName('coreModel')
            inputs.files(project.fileTree(dir: 'modules', include: '**/src-db/database/model/**/*.xml')).withPropertyName('modulesModel')
            if (project.file('modules_core').exists()) {
                inputs.files(project.fileTree(dir: 'modules_core', include: '**/src-db/database/model/**/*.xml')).withPropertyName('modulesCoreModel')
            }

            // --- OUTPUTS: Lo que WAD genera ---
            outputs.dir('srcAD').withPropertyName('generatedADSource')
            outputs.file('build/javasqlc/src/web.xml').withPropertyName('generatedWebXml')

            doLast {
                // Construct classpath natively to avoid StackOverflow with Ant references
                def wadClasspath = project.files('src-wad/lib/openbravo-wad.jar') +
                                    project.files('src-core/lib/openbravo-core.jar') +
                                    project.files('src-wad/src') + // Templates for WAD
                                    project.files('src') +         // Root templates
                                    project.configurations.findByName('compileClasspath') +
                                    project.fileTree(dir: 'lib', include: '**/*.jar') +
                                    project.files('config')

                def baseConfig = project.file('config').absolutePath
                
                // Read properties
                def getProp = { key, defVal ->
                    if (project.hasProperty(key)) return project.property(key)
                    // Check Ant properties
                    if (project.ant.properties.containsKey(key)) return project.ant.properties[key]
                    return defVal
                }

                def webUrl = getProp('web.url', 'http://localhost:8080/etendo/web')
                def attachPath = getProp('attach.path', project.file('attachments').absolutePath)

                project.javaexec {
                    mainClass = 'org.openbravo.wad.Wad'
                    classpath = wadClasspath
                    maxHeapSize = '1536m' 
                    jvmArgs = ["-Djava.security.egd=file:///dev/urandom"]
                    
                    // Args matching src/build.xml 'wad' task
                    args = [
                        baseConfig,
                        "%", // tab
                        project.file('srcAD/org/openbravo/erpWindows').absolutePath,
                        project.file('srcAD/org/openbravo/erpCommon').absolutePath,
                        project.file('build/javasqlc/src').absolutePath,
                        "all", // webTab
                        project.file('srcAD/org/openbravo/erpCommon/ad_actionButton').absolutePath,
                        project.file('WebContent/src-loc').absolutePath, // baseDesign
                        "org/openbravo/erpWindows", // base.translate.structure
                        "dummyValueUnused",
                        "..",
                        attachPath,
                        webUrl,
                        project.file('src').absolutePath,
                        "true", // complete
                        "%", // module
                        "noquick", // quick
                        "false", // wad.generateAllClassic250Windows
                        "false"  // exclude.cdi
                    ]
                }
            }
        }
    }
}