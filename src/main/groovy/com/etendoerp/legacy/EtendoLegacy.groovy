package com.etendoerp.legacy

import com.etendoerp.legacy.dependencies.ResolverDependencyLoader
import com.etendoerp.legacy.modules.ExpandModulesLoader
import com.etendoerp.legacy.modules.ModuleZipLoader
import org.gradle.BuildListener
import org.gradle.BuildResult;
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

class EtendoLegacy {

    static void load(Project project) {
        ExpandModulesLoader.load(project)
        LegacyScriptLoader.load(project)
        ModuleZipLoader.load(project)
        ResolverDependencyLoader.load(project)

        project.gradle.addBuildListener(new BuildListener() {
            void buildStarted(Gradle gradle) {}
            void settingsEvaluated(Settings settings) {}
            void projectsLoaded(Gradle gradle) {}
            void projectsEvaluated(Gradle gradle) {}
            void buildFinished(BuildResult result) {
                try {
                    def buildFile = project.getBuildFile()
                    def content = buildFile.getText()
                    def lines = content.split('\n')

                    // Pattern for space-separated style (e.g., id 'plugin_id' version 'latest.release')
                    def pattern1 = ~"id\\s+(['\"'])[^'\"]*\\1\\s+version\\s+(['\"'])latest.release\\2"

                    // Pattern for method call style (e.g., id('plugin_id') version('latest.release'))
                    def pattern2 = ~"id\\(['\"']([^'\"]*['\"'])\\)\\s+version\\(['\"']latest.release['\"']\\)"

                    def warning = false
                    for (line in lines) {
                        def matcher1 = line =~ pattern1
                        if (matcher1.find()) {
                            def pluginId = matcher1[0][0].trim()
                            println "\u001B[33m" + "*".repeat(80) + "\u001B[0m"
                            println "\u001B[33m" + " " * 10 + "!!! DANGER ZONE ALERT !!!" + " " * 10 + "\u001B[0m"
                            println "\u001B[33m" + "=".repeat(80) + "\u001B[0m"
                            println "\u001B[33m[WARNING] PLUGIN '$pluginId' - "
                            println "\u001B[33m" + " " * 5 + " THIS IS ABSOLUTELY NOT SUPPORTED!!!\u001B[0m"
                            println "\u001B[33m" + " " * 5 + ">>> FIX IT NOW: USE A SPECIFIC VERSION <<<" + " " * 5 + "\u001B[0m"
                            println "\u001B[33m" + " " * 5 + ">>> NEXT RELEASE WILL BLOCK COMPILATION <<<" + " " * 5 + "\u001B[0m"
                            println "\u001B[33m" + "=".repeat(80) + "\u001B[0m"
                            println "\u001B[33m" + "*".repeat(80) + "\u001B[0m"
                            warning = true
                            break
                        }

                        def matcher2 = line =~ pattern2
                        if (matcher2.find()) {
                            def pluginId = matcher2[0][1].trim()
                            println "\u001B[33m" + "*".repeat(80) + "\u001B[0m"
                            println "\u001B[33m" + " " * 10 + "!!! DANGER ZONE ALERT !!!" + " " * 10 + "\u001B[0m"
                            println "\u001B[33m" + "=".repeat(80) + "\u001B[0m"
                            println "\u001B[33m[WARNING] PLUGIN '$pluginId' - THIS IS ABSOLUTELY NOT SUPPORTED!!!\u001B[0m"
                            println "\u001B[33m" + " " * 5 + ">>> FIX IT NOW: USE A SPECIFIC VERSION <<<" + " " * 5 + "\u001B[0m"
                            println "\u001B[33m" + "=".repeat(80) + "\u001B[0m"
                            println "\u001B[33m" + "*".repeat(80) + "\u001B[0m"
                            warning = true
                        }
                    }
                } catch (Exception e) {
                    println "\u001B[31m" + "Error: ${e.message}" + "\u001B[0m"
                }
            }
        })
    }
}
