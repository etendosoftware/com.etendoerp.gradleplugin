package com.etendoerp.dependencies

import com.etendoerp.legacy.utils.NexusUtils
import org.gradle.api.Project

class EtendoCoreDependencies {

    static void loadCoreCompilationDependencies(Project project) {
        // Listing jar files - scope: COMPILATION
        // Total artifacts      = 88
        // Resolved artifacts   = 88
        // Unresolved artifacts = 0

        def (nexusUser, nexusPassword) = NexusUtils.getCredentials(project)

        project.repositories {
            mavenCentral()
            maven {
                url 'https://repo.futit.cloud/repository/etendo-public-jars'
                credentials {
                    username "${nexusUser}"
                    password "${nexusPassword}"
                }
            }
        }

        // Listing only resolved artifacts.
        // Load from $projectDir/artifacts.list.COMPILATION.gradle


        project.dependencies {
            ext.dependenciesListCOMPILATION.each { dependency ->
                implementation dependency
            }
        }
    }

    static void loadCoreTestDependencies(Project project) {
        // Listing jar files - scope: TEST
        // Total artifacts      = 29
        // Resolved artifacts   = 29
        // Unresolved artifacts = 0

        def (nexusUser, nexusPassword) = NexusUtils.getCredentials(project)

        project.repositories {
            mavenCentral()
            maven {
                url 'https://repo.futit.cloud/repository/etendo-public-jars'
                credentials {
                    username "${nexusUser}"
                    password "${nexusPassword}"
                }
            }
        }

        // Listing only resolved artifacts.
        project.dependencies {
            testImplementation('org.hamcrest:hamcrest-all:1.3') { transitive = false }
            testImplementation('junit:junit:4.12') { transitive = false }
            testImplementation('org.jboss.arquillian.core:arquillian-core-impl-base:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.junit:arquillian-junit-container:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.test:arquillian-test-api:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.config:arquillian-config-api:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-impl-base:1.4.1.Final') { transitive = false }
            testImplementation('com.etendoerp:jboss-ejb3-api:3.1.0') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-weld-embedded:2.0.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.junit:arquillian-junit-core:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-test-api:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-test-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.shrinkwrap:shrinkwrap-spi:1.2.6') { transitive = false }
            testImplementation('org.jboss.arquillian.config:arquillian-config-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.testenricher:arquillian-testenricher-cdi:1.4.1.Final') { transitive = false }
            testImplementation('org.reflections:reflections:0.9.11') { transitive = false }
            testImplementation('org.jboss.arquillian.core:arquillian-core-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.shrinkwrap.descriptors:shrinkwrap-descriptors-spi:2.0.0') { transitive = false }
            testImplementation('org.jboss.shrinkwrap.descriptors:shrinkwrap-descriptors-api-base:2.0.0') { transitive = false }
            testImplementation('org.jboss.arquillian.test:arquillian-test-spi:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.config:arquillian-config-impl-base:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.shrinkwrap:shrinkwrap-api:1.2.6') { transitive = false }
            testImplementation('org.jboss.arquillian.core:arquillian-core-api:1.4.1.Final') { transitive = false }
            testImplementation('org.jboss.shrinkwrap:shrinkwrap-impl-base:1.2.6') { transitive = false }
            testImplementation('org.jboss.arquillian.test:arquillian-test-impl-base:1.4.1.Final') { transitive = false }
            testImplementation('org.eu.ingwar.tools:arquillian-suite-extension:1.2.0') { transitive = false }
            testImplementation('org.jboss.spec.javax.el:jboss-el-api_3.0_spec:1.0.13.Final') { transitive = false }
            testImplementation('org.jboss.arquillian.container:arquillian-container-test-impl-base:1.4.1.Final') { transitive = false }
        }
    }

}
