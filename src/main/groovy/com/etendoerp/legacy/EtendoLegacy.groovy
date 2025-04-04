package com.etendoerp.legacy

import com.etendoerp.legacy.dependencies.ResolverDependencyLoader
import com.etendoerp.legacy.modules.ExpandModulesLoader
import com.etendoerp.legacy.modules.ModuleZipLoader
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

class EtendoLegacy {

    static final String COLOR_YELLOW = "\u001B[33m"
    static final String RESET_COLOR = "\u001B[0m"
    static final int WIDTH_MARGIN = 20
    static final int WIDTH_LINE = 80
    static final String LINE = "=" * WIDTH_LINE
    static final String MARGIN = "-" * WIDTH_MARGIN

    static void load(Project project) {
        ExpandModulesLoader.load(project)
        LegacyScriptLoader.load(project)
        ModuleZipLoader.load(project)
        ResolverDependencyLoader.load(project)

        project.gradle.addBuildListener(new BuildListener() {

            @SuppressWarnings("UnusedMethodParameter")
            @Override
            void beforeSettings(Settings settings) {
                super.beforeSettings(settings)
                project.logger.debug("Etendo Legacy Plugin: before settings")
            }

            @SuppressWarnings("UnusedMethodParameter")
            @Override
            void settingsEvaluated(Settings settings) {
                project.logger.debug("Etendo Legacy Plugin: settings evaluated")
            }

            @SuppressWarnings("UnusedMethodParameter")
            @Override
            void projectsLoaded(Gradle gradle) {
                project.logger.debug("Etendo Legacy Plugin: projects loaded")
            }

            @SuppressWarnings("UnusedMethodParameter")
            @Override
            void projectsEvaluated(Gradle gradle) {
                project.logger.debug("Etendo Legacy Plugin: projects evaluated")
            }

            @Override
            void buildFinished(BuildResult result) {
                project.logger.debug("Etendo Legacy Plugin: build finished with status: ${result.failure ? 'FAILED' : 'SUCCESS'}")
                if (result.failure) {
                    project.logger.warn("Skipping plugin version check because the build failed.")
                    return
                }
                def buildFile = project.getBuildFile()
                def content = buildFile.getText()
                def lines = content.split('\n')

                // Pattern for space-separated style (e.g., id 'plugin_id' version 'latest.release')
                def pattern1 = ~"id\\s+(['\"'])[^'\"]*\\1\\s+version\\s+(['\"'])latest.release\\2"

                // Pattern for method call style (e.g., id('plugin_id') version('latest.release'))
                def pattern2 = ~"id\\(['\"']([^'\"]*['\"'])\\)\\s+version\\(['\"']latest.release['\"']\\)"

                for (line in lines) {
                    def matcher1 = line =~ pattern1
                    if (matcher1.find()) {
                        def pluginId = matcher1[0][0].trim()
                        message(pluginId, project)
                        break
                    }

                    def matcher2 = line =~ pattern2
                    if (matcher2.find()) {
                        def pluginId = matcher2[0][1].trim()
                        message(pluginId, project)
                        break
                    }
                }
            }
        })

    }

    static void message(String pluginId, Project project) {
        project.logger.warn COLOR_YELLOW + LINE
        project.logger.warn " " * 10 + "!!! DANGER ZONE ALERT !!!"
        project.logger.warn LINE
        project.logger.warn "[WARNING] PLUGIN '$pluginId' - "
        project.logger.warn MARGIN + "THIS IS ABSOLUTELY NOT SUPPORTED!!!"
        project.logger.warn MARGIN + ">>> FIX IT NOW: USE A SPECIFIC VERSION <<<"
        project.logger.warn MARGIN + ">>> NEXT RELEASE WILL BLOCK COMPILATION <<<"
        project.logger.warn LINE + RESET_COLOR
    }
}
