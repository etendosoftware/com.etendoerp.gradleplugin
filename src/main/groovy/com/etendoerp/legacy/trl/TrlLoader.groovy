package com.etendoerp.legacy.trl

import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar

class TrlLoader {

    static void load(Project project) {
        createTrlSqlcTask(project)
        createTrlJarTask(project)
        configureSourceSets(project)
    }

    private static void createTrlSqlcTask(Project project) {
        project.tasks.register('gradleTrlSqlc') {
            description = 'Generates Java code from .xsql files for TRL'
            group = 'etendo-trl'

            dependsOn 'prepareConfig'

            inputs.files(project.fileTree(dir: 'src-trl/src', include: '**/*.xsql')).withPropertyName('trlXsql')
            def prepareConfig = project.tasks.named('prepareConfig')
            inputs.files(prepareConfig).withPropertyName('config')
            
            def outputDir = project.file('build/javasqlc/trl/src')
            outputs.dir(outputDir).withPropertyName('generatedTrlJava')
            outputs.cacheIf { true }

            doLast {
                outputDir.mkdirs()
                def openbravoProperties = prepareConfig.get().outputs.files.find { it.name == 'Openbravo.properties' }

                def sqlcClasspath = project.files('src-core/lib/openbravo-core.jar') + 
                                    project.configurations.findByName('compileClasspath') +
                                    project.files('config')

                def baseConfig = project.file('config').absolutePath
                def log4jConfig = project.file('build/etendo/log4j2-no-db.xml')

                if (project.file('src-trl/src').exists()) {
                    project.javaexec {
                        workingDir = project.file('src-trl/src')
                        mainClass = 'org.openbravo.data.Sqlc'
                        classpath = sqlcClasspath
                        jvmArgs = [
                            "-Djava.security.egd=file:///dev/urandom",
                            "-Dlog4j.configurationFile=${log4jConfig.absolutePath}",
                            "-Dorg.openbravo.utils.OBRebuildAppender.disabled=true",
                            "-Dsqlc.queryExecutionStrategy=traditional"
                        ]
                        maxHeapSize = '1024m'
                        
                        args = [
                            openbravoProperties.absolutePath,
                            ".xsql",
                            '.',
                            outputDir.absolutePath,
                            "null",
                            "false"
                        ]
                    }
                }
            }
        }
    }

    private static void createTrlJarTask(Project project) {
        project.tasks.register('gradleTrlJar', Jar) {
            description = 'Creates openbravo-trl.jar'
            group = 'etendo-trl'
            
            dependsOn 'compileJava'
            dependsOn 'gradleCopyModuleBuildResources'
            dependsOn 'gradleCopyResources'
            
            archiveFileName = 'openbravo-trl.jar'
            destinationDirectory = project.layout.projectDirectory.dir('src-trl/lib').asFile

            // Trl classes are compiled into build/classes along with everything else
            from(project.file('build/classes')) {
                include 'org/openbravo/translate/**'
            }
        }
    }

    private static void configureSourceSets(Project project) {
        project.afterEvaluate {
            def mainSourceSet = project.sourceSets.main
            mainSourceSet.java.srcDir 'src-trl/src'
            
            def compileJavaTask = project.tasks.findByName('compileJava')
            if (compileJavaTask != null) {
                compileJavaTask.dependsOn('gradleTrlSqlc')
            }
        }
    }
}
