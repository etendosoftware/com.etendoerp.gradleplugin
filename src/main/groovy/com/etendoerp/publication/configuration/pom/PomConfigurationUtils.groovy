package com.etendoerp.publication.configuration.pom

import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.publish.maven.MavenPom

import javax.annotation.Nullable

class PomConfigurationUtils {

    static void configurePomDependencies(Project subProject, Configuration configuration, MavenPom pom) {
        pom.withXml({
            Map<String, Dependency> dependencyMap = ResolverDependencyUtils.loadDependenciesMap(subProject, configuration)
            Map<String, Dependency> updatedDependencies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)

            NodeList dependenciesNodeList = it.asNode().dependencies

            if (dependenciesNodeList && dependencyMap && !dependencyMap.isEmpty()) {
                updateDependenciesNode(dependenciesNodeList, dependencyMap, updatedDependencies)
            }
        })
    }

    static Node updateDependenciesNode(NodeList dependenciesNodeList, Map<String, Dependency> dependencyMap, Map<String, Dependency> updatedDependencies) {

        Node dependenciesNode = new Node(null, "dependencies")

        dependenciesNodeList.dependency.each { Node dep ->
            String groupId = dep.groupId.last().value().last()
            String artifactId = dep.artifactId.last().value().last()
            String name = "${groupId}.${artifactId}"

            // Update node version
            if (dependencyMap && dependencyMap.containsKey(name)) {
                Dependency dependency = dependencyMap.remove(name)

                NodeList versionNodeList = dep.version.last().value()
                versionNodeList.removeAll {true}
                versionNodeList.add(dependency.version)
                updatedDependencies.put(name, dependency)
            }
            String version = dep.version.last().value()
            dependenciesNode.append(createDependencyNode(groupId, artifactId, version))
        }

        return dependenciesNode
    }

    static Node createDependencyNode(Dependency dependency, String scope="runtime") {
        createDependencyNode(dependency.group, dependency.name, dependency.version, scope)
    }

    static Node createDependencyNode(String group, String artifact, String version, String scope="runtime") {
        def dependencyNode = new Node(null, "dependency")
        dependencyNode.appendNode("groupId", group)
        dependencyNode.appendNode("artifactId", artifact)
        dependencyNode.appendNode("version", version)
        dependencyNode.appendNode("scope", scope)
        return dependencyNode
    }

    static Node generateDependenciesNode(@Nullable Node dependenciesNode, Map<String, Dependency> dependencyMap) {
        if (!dependenciesNode) {
            dependenciesNode = new Node(null, "dependencies")
        }

        for (def entry : dependencyMap) {
            Node dependencyNode = createDependencyNode(entry.value)
            dependenciesNode.append(dependencyNode)
        }

        return dependenciesNode
    }


}
