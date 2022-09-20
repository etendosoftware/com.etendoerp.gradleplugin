package com.etendoerp.legacy.modules.expand

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.legacy.LegacyScriptLoader
import com.etendoerp.legacy.ant.AntMenuHelper
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.EtendoCoreZipArtifact
import com.etendoerp.legacy.utils.NexusUtils
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync

class ExpandCore {

    static final String FORCE_EXPAND_PROP = "forceExpand"

    static void load(Project project) {

        project.tasks.register("cleanTempCoreDir", Delete) {
            delete({
                project.tasks.findByName("expandCoreConfig").getTemporaryDir()
            })
        }

        project.tasks.register("expandCoreConfig") {
            def tempDir = it.getTemporaryDir()
            doLast {
                CoreMetadata coreMetadata = new CoreMetadata(project)

                // Clean dir
                project.delete(tempDir)

                // Load the core metadata from the extension with the user options
                coreMetadata.loadMetadataFromExtension()

                def extension = project.extensions.findByType(EtendoPluginExtension)

                // Verify if the core is already in JAR
                // The core is in JAR and neither the force property or the ignore Core Jar Dependency is set to true.
                if (coreMetadata.isCoreInJars() && !(project.findProperty(FORCE_EXPAND_PROP) || extension.ignoreCoreJarDependency)) {
                    throw new IllegalStateException("* The CORE can not be expanded." +
                                                    "${EtendoPluginExtension.ignoreCoreJarDependencyMessage()} \n" +
                                                    "* To force the expansion use the command line parameter '-P${FORCE_EXPAND_PROP}=true'")
                }

                def performResolutionConflicts = extension.performResolutionConflicts
                def supportJars = extension.supportJars

                ArtifactDependency coreArtifactDependency = null
                String displayName = coreMetadata.coreId

                NexusUtils.askNexusCredentials(project)

                project.logger.info("*****************************************************")
                project.logger.info("* Starting expanding the core in SOURCES.")
                project.logger.info("* '${coreMetadata.coreId}'")
                project.logger.info("*****************************************************")

                if (performResolutionConflicts) {
                    project.logger.info("* Running the resolution of conflicts of the 'expandCore' task.")
                    def artifactDependencies = ExpandUtils.performExpandResolutionConflicts(project, coreMetadata, true, supportJars, false, true)

                    // Obtain the 'selected' Core version
                    String currentCoreDependency = "${coreMetadata.coreGroup}:${coreMetadata.coreName}"
                    coreArtifactDependency = ResolverDependencyUtils.getCoreDependency(project, currentCoreDependency ,artifactDependencies)
                }

                // Check the version to expand
                if (!coreArtifactDependency || (coreArtifactDependency && coreArtifactDependency.hasConflicts)) {
                    displayName = coreMetadata.coreId
                    project.logger.info("***********************************************")
                    project.logger.info("* The core dependency to resolve will be the one defined by the user")
                    project.logger.info("* Core SOURCES dependency '${displayName}'")
                    project.logger.info("***********************************************")
                } else {
                    // If the core does not have conflicts, update the Core dependency with the resolved 'selected' dependency
                    displayName = coreArtifactDependency.displayName
                    project.logger.info("***********************************************")
                    project.logger.info("* Core SOURCES dependency resolved to use '${displayName}'")
                    project.logger.info("***********************************************")
                }

                coreArtifactDependency = ExpandUtils.collectArtifactDependencyFile(project, displayName, "zip", true)

                if (coreArtifactDependency == null) {
                    throw new IllegalArgumentException("The core dependency '${displayName}' could not be resolved.")
                }

                if (coreArtifactDependency instanceof EtendoCoreZipArtifact) {
                    if (shouldExpandCore(project, coreArtifactDependency)) {
                        def core = coreArtifactDependency as EtendoCoreZipArtifact
                        core.tempDir = tempDir
                        core.extract()
                    }
                } else {
                    throw new IllegalArgumentException("The artifact dependency to expand is not a instance of a EtendoCoreZipArtifact. Artifact: ${displayName}")
                }
            }
        }

        project.tasks.register("expandCore", Sync) {
            def expandConfigTask = project.tasks.findByName("expandCoreConfig")
            dependsOn({
                expandConfigTask
            })

            finalizedBy({
                project.tasks.findByName("cleanTempCoreDir")
            })

            from(expandConfigTask.getTemporaryDir())
            into("${project.projectDir}")

            preserve {
                include '**'
                exclude(LegacyScriptLoader.whiteSyncCoreList)
            }

            doLast {
                project.logger.info("*** The expandCore task completed successfully. ***")
            }
        }
    }

    static boolean shouldExpandCore(Project mainProject, EtendoCoreZipArtifact coreDisplayName) {
        String message = """
        |*************** CORE EXPANSION ***************
        |* Core version to expand: ${coreDisplayName.displayName}
        |* The expansion will overwrite and delete all the current core files.
        |""".stripMargin()
        return AntMenuHelper.confirmationMenu(mainProject, message)
    }
}
