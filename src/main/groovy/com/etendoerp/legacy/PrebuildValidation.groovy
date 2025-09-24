package com.etendoerp.legacy


import com.etendoerp.legacy.utils.AnsiColor
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

class PrebuildValidation {

    static void load(Project project) {
        //Prebuild Validation
        project.gradle.addBuildListener(new BuildListener() {
            void buildStarted(Gradle gradle) {}

            void settingsEvaluated(Settings settings) {}

            void projectsLoaded(Gradle gradle) {}

            void projectsEvaluated(Gradle gradle) {}

            void buildFinished(BuildResult result) {
                /* validate if etendo object was used */
                def buildFile = project.getBuildFile()
                def content = buildFile.getText("UTF-8")
                //remove the commentS /* */ AND //
                content = content.replaceAll(/(?s)\/\*.*?\*\//, "")
                content = content.replaceAll(/(?m)^\s*\/\/.*$/, "")
                def etendoBlockPattern = /etendo\s*\{[^}]*\}/
                def matcher = content =~ etendoBlockPattern
                //find "blocks" etendo{ something} can be more than one. each block is loaded with a config slurper
                def supportJars = true
                if (matcher) {
                    matcher.each { match ->
                        def cslp = new ConfigSlurper().parse(match.toString())
                        if (cslp.containsKey("etendo") && cslp.get("etendo").containsKey("supportJars")) {
                            supportJars = cslp.get("etendo").get("supportJars")
                        }
                    }
                }

                if (supportJars) {
                    return
                }
                //if supportJars is false, must fail if there are implmenentations
                // dependencies obiously ignoring lines starting with //
                def dependenciesBlockPattern = /dependencies\s*\{[^}]*\}/
                def dpmatcher = content =~ dependenciesBlockPattern
                if (dpmatcher) {
                    def allowBuild = false
                    dpmatcher.each { match ->
                        def lines = match.readLines()
                        def isInComment = false
                        lines.each { line ->
                            def trimmedLine = line.trim()
                            if (isInComment && trimmedLine.endsWith("*/")) {
                                isInComment = false
                                return
                            }
                            if (trimmedLine.startsWith("/*")) {
                                isInComment = true
                                return
                            }
                            if (!allowBuild && !isInComment && trimmedLine.startsWith("implementation")) {
                                StringBuilder sb = new StringBuilder()
                                sb.append("\n")
                                sb.append("${AnsiColor.YELLOW}")
                                sb.append("*".repeat(80))
                                sb.append("\n")
                                sb.append(" " * 32 + "!!! WARNING !!!" + " " * 32)
                                sb.append("\n\n")
                                sb.append("When using 'etendo { supportJars = false }' you cannot have 'implementation' dependencies in your build.gradle file. Found: ${buildFile}\n")
                                sb.append("Issue Line:\n    ${trimmedLine}")
                                sb.append("\n\n")
                                sb.append("If you want to use implementation dependencies please set 'supportJars = true' or change the implementation to 'moduleDeps' if it's an Etendo module.\n")
                                sb.append("Do you want to proceed anyways? If so type exactly 'yes'. If not just press enter to cancel the build.\n")
                                sb.append("*".repeat(80))
                                sb.append("\n")
                                sb.append("${AnsiColor.RESET}")
                                String string = sb.toString()
                                println string
                                def userInput = new BufferedReader(new InputStreamReader(System.in)).readLine()?.trim()
                                if (userInput == "yes") {
                                    allowBuild = true
                                    return
                                }
                                throw new IllegalStateException("Build cancelled by user.")

                            }
                        }
                    }
                }
            }
        })
    }
}