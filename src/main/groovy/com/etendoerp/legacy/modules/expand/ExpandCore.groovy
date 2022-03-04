package com.etendoerp.legacy.modules.expand

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.core.CoreType
import com.etendoerp.legacy.LegacyScriptLoader
import com.etendoerp.legacy.dependencies.ResolutionUtils
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.EtendoCoreZipArtifact
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync

class ExpandCore {

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

                // Load the core metadata from the extension with the user options
                coreMetadata.loadMetadataFromExtension()

                def extension = project.extensions.findByType(EtendoPluginExtension)

                def performResolutionConflicts = extension.performResolutionConflicts
                def supportJars = extension.supportJars

                ArtifactDependency coreArtifactDependency = null
                String displayName = coreMetadata.coreId

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

                coreArtifactDependency = ExpandUtils.collectArtifactDependencyFile(project, displayName, "zip")

                if (coreArtifactDependency instanceof EtendoCoreZipArtifact) {
                    def core = coreArtifactDependency as EtendoCoreZipArtifact
                    core.tempDir = tempDir
                    core.extract()
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
                project.logger.info("The core was extracted successfully.")
            }

        }

    }

}
