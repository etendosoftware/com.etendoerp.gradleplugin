package com.etendoerp.dependencies.repository

import org.gradle.api.Project

class EtendoRepositoryLoader {

    private static final String DEFAULT_GITHUB_PACKAGES_REPOSITORY_URL =
            "https://maven.pkg.github.com/etendosoftware/etendo_core"

    static void load(Project project) {
        Project rootProject = project.rootProject
        String githubPackagesRepositoryUrl = findProperty(rootProject, "githubPackagesRepositoryUrl",
                DEFAULT_GITHUB_PACKAGES_REPOSITORY_URL)
        String githubPackagesRepositoryUser = findProperty(rootProject, "githubPackagesRepositoryUser",
                findProperty(rootProject, "githubUser", ""))
        String githubPackagesRepositoryPassword = findProperty(rootProject, "githubPackagesRepositoryPassword",
                findProperty(rootProject, "githubToken", ""))
        String nexusUser = findProperty(rootProject, "nexusUser", "")
        String nexusPassword = findProperty(rootProject, "nexusPassword", "")

        rootProject.allprojects { targetProject ->
            configureRepositories(
                    targetProject,
                    githubPackagesRepositoryUrl,
                    githubPackagesRepositoryUser,
                    githubPackagesRepositoryPassword,
                    nexusUser,
                    nexusPassword
            )
        }
    }

    private static void configureRepositories(Project targetProject, String githubPackagesRepositoryUrl,
                                              String githubPackagesRepositoryUser,
                                              String githubPackagesRepositoryPassword, String nexusUser,
                                              String nexusPassword) {
        addMavenRepository(targetProject, "https://repo.maven.apache.org/maven2/")
        addMavenRepository(targetProject, githubPackagesRepositoryUrl) {
            credentials {
                username = githubPackagesRepositoryUser
                password = githubPackagesRepositoryPassword
            }
        }
        addMavenRepository(targetProject, "https://repo.futit.cloud/repository/maven-public-releases")
        addMavenRepository(targetProject, "https://repo.futit.cloud/repository/maven-releases") {
            credentials {
                username = nexusUser
                password = nexusPassword
            }
        }
        addMavenRepository(targetProject, "https://repo.futit.cloud/repository/maven-public-jars")
        addMavenRepository(targetProject, "https://repo.futit.cloud/repository/etendo-public-jars")
    }

    private static void addMavenRepository(Project targetProject, String repositoryUrl, Closure repositoryConfig = null) {
        def existingRepository = targetProject.repositories.find { repository ->
            normalizeUrl(repository.properties.get("url")?.toString()).equalsIgnoreCase(normalizeUrl(repositoryUrl))
        }
        if (existingRepository) {
            return
        }

        targetProject.repositories.maven {
            url = URI.create(repositoryUrl)
            if (repositoryUrl.startsWith("http://")) {
                allowInsecureProtocol = true
            }
            if (repositoryConfig) {
                repositoryConfig.delegate = delegate
                repositoryConfig.resolveStrategy = Closure.DELEGATE_FIRST
                repositoryConfig()
            }
        }
    }

    private static String findProperty(Project project, String propertyName, String defaultValue) {
        def value = project.findProperty(propertyName)
        String propertyValue = value == null ? "" : value.toString().trim()
        return propertyValue ? propertyValue : defaultValue
    }

    private static String normalizeUrl(String repositoryUrl) {
        return repositoryUrl == null ? "" : repositoryUrl.replaceAll("/+\$", "")
    }
}
