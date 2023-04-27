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
        project.dependencies {
            implementation('org.apache.tomcat:tomcat-servlet-api:8.5.47') { transitive = false }
            implementation('com.etendoerp:YUIAnt:1.0.0') { transitive = false }
            implementation('com.etendoerp:yuicompressor:2.4.2') { transitive = false }
            implementation('commons-collections:commons-collections:3.2.2') { transitive = false }
            implementation('com.etendoerp:jasperreports-fonts:6.0.0') { transitive = false }
            implementation('com.etendoerp:antlr:2.7.7') { transitive = false }
            implementation('org.jvnet.staxex:stax-ex:1.8') { transitive = false }
            implementation('org.glassfish.jaxb:jaxb-runtime:2.3.1') { transitive = false }
            implementation('com.etendoerp:itextpdf:5.5.0') { transitive = false }
            implementation('com.fasterxml.jackson.core:jackson-core:2.11.2') { transitive = false }
            implementation('commons-logging:commons-logging:1.2') { transitive = false }
            implementation('com.etendoerp:jasperreports:6.0.0') { transitive = false }
            implementation('com.sun.xml.fastinfoset:FastInfoset:1.2.15') { transitive = false }
            implementation('jfree:jcommon:1.0.15') { transitive = false }
            implementation('org.eclipse.jdt:ecj:3.23.0') { transitive = false }
            implementation('com.sun.mail:javax.mail:1.6.1') { transitive = false }
            implementation('org.jboss.logging:jboss-logging:3.3.2.Final') { transitive = false }
            implementation('com.etendoerp:jettison:1.3') { transitive = false }
            implementation('org.jboss.spec.javax.transaction:jboss-transaction-api_1.2_spec:1.1.1.Final') { transitive = false }
            implementation('org.javassist:javassist:3.24.0-GA') { transitive = false }
            implementation('org.apache.logging.log4j:log4j-web:2.16.0') { transitive = false }
            implementation('org.postgresql:postgresql:42.5.4') { transitive = false }
            implementation('nekohtml:nekohtml:0.9.5') { transitive = false }
            implementation('org.apache.logging.log4j:log4j-core:2.16.0') { transitive = false }
            implementation('commons-fileupload:commons-fileupload:1.4') { transitive = false }
            implementation('com.sun.activation:javax.activation:1.2.0') { transitive = false }
            implementation('com.sun.istack:istack-commons-runtime:3.0.7') { transitive = false }
            implementation('org.apache.logging.log4j:log4j-1.2-api:2.16.0') { transitive = false }
            implementation('commons-io:commons-io:2.4') { transitive = false }
            implementation('com.etendoerp:catalina-ant:1.0.0') { transitive = false }
            implementation('org.quartz-scheduler:quartz:2.3.2') { transitive = false }
            implementation('com.etendoerp:commons-lang:2.6') { transitive = false }
            implementation('org.mozilla:rhino:1.7.13') { transitive = false }
            implementation('org.hibernate.common:hibernate-commons-annotations:5.1.0.Final') { transitive = false }
            implementation('org.apache.poi:poi:3.10.1') { transitive = false }
            implementation('com.etendoerp:rhino-engine:1.7.13') { transitive = false }
            implementation('commons-pool:commons-pool:1.5.6') { transitive = false }
            implementation('com.fasterxml.jackson.core:jackson-databind:2.11.2') { transitive = false }
            implementation('org.jboss:jandex:2.0.5.Final') { transitive = false }
            implementation('commons-digester:commons-digester:1.8.1') { transitive = false }
            implementation('org.apache.tika:tika-core:0.9') { transitive = false }
            implementation('com.etendoerp:ant-nodeps:1.0.0') { transitive = false }
            implementation('org.redisson:redisson:3.15.4') { transitive = false }
            implementation('org.apache.logging.log4j:log4j-slf4j-impl:2.16.0') { transitive = false }
            implementation('org.apache.ant:ant-launcher:1.9.2') { transitive = false }
            implementation('javax.xml.bind:jaxb-api:2.3.1') { transitive = false }
            implementation('com.fasterxml.jackson.core:jackson-annotations:2.11.2') { transitive = false }
            implementation('jfree:jfreechart:1.0.12') { transitive = false }
            implementation('com.fasterxml:classmate:1.3.4') { transitive = false }
            implementation('org.hibernate:hibernate-core:5.4.2.Final') { transitive = false }
            implementation('commons-beanutils:commons-beanutils:1.8.3') { transitive = false }
            implementation('org.dom4j:dom4j:2.1.3') { transitive = false }
            implementation('org.freemarker:freemarker:2.3.16') { transitive = false }
            implementation('com.google.guava:guava:21.0') { transitive = false }
            implementation('javax.persistence:javax.persistence-api:2.2') { transitive = false }
            implementation('org.apache.logging.log4j:log4j-api:2.16.0') { transitive = false }
            implementation('com.etendoerp:itext-pdfa:5.5.0') { transitive = false }
            implementation('xerces:xercesImpl:2.9.0') { transitive = false }
            implementation('org.apache.ant:ant:1.9.2') { transitive = false }
            implementation('com.etendoerp:slf4j-api:1.7.25') { transitive = false }
            implementation('commons-codec:commons-codec:1.11') { transitive = false }
            implementation('commons-dbcp:commons-dbcp:1.4') { transitive = false }
            implementation('org.glassfish.jaxb:txw2:2.3.1') { transitive = false }
            implementation('net.sourceforge.jexcelapi:jxl:2.6.10') { transitive = false }
            implementation('net.bytebuddy:byte-buddy:1.9.10') { transitive = false }
            implementation('oro:oro:2.0.8') { transitive = false }
            implementation('com.etendoerp:wstx-asl:3.0.2') { transitive = false }
            implementation('commons-betwixt:commons-betwixt:0.8') { transitive = false }
            implementation('org.apache.tomcat:tomcat-jdbc:9.0.37') { transitive = false }
            implementation('org.apache.tomcat:tomcat-juli:9.0.37') { transitive = false }
            implementation('com.auth0:java-jwt:3.1.0') { transitive = false }
            implementation('cz.jirutka.rsql:rsql-parser:2.1.0') { transitive = false }
            implementation('org.apache.httpcomponents:httpmime:4.5.5') { transitive = false }
            implementation('org.apache.httpcomponents:httpclient:4.5.5') { transitive = false }
            implementation('org.apache.httpcomponents:httpcore:4.4.9') { transitive = false }
            implementation('javax.enterprise:cdi-api:2.0.SP1') { transitive = false }
            implementation('org.jboss.weld:weld-api:3.1.Final') { transitive = false }
            implementation('org.jboss.weld.module:weld-jsf:3.1.1.Final') { transitive = false }
            implementation('org.jboss.spec.javax.interceptor:jboss-interceptors-api_1.2_spec:1.0.0.Final') { transitive = false }
            implementation('org.jboss.weld.module:weld-web:3.1.1.Final') { transitive = false }
            implementation('org.jboss.weld:weld-spi:3.1.Final') { transitive = false }
            implementation('org.jboss.classfilewriter:jboss-classfilewriter:1.2.4.Final') { transitive = false }
            implementation('org.jboss.weld:weld-core-impl:3.1.1.Final') { transitive = false }
            implementation('org.jboss.spec.javax.annotation:jboss-annotations-api_1.3_spec:1.0.0.Final') { transitive = false }
            implementation('org.jboss.weld.environment:weld-environment-common:3.1.1.Final') { transitive = false }
            implementation('org.jboss.weld.servlet:weld-servlet-core:3.1.1.Final') { transitive = false }
            implementation('javax.inject:javax.inject:1') { transitive = false }
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
