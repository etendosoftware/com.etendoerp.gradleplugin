# 📋 Plan de Implementación: Tarea `setup.applyTemplates`

**Fecha**: 2 de febrero de 2026  
**Requisitos**: Ver `Setup: Tarea Gradle para aplicar templates de configuración`

## 🏗️ Estructura de Archivos a Crear

```
buildSrc/
├── src/
│   └── main/
│       ├── groovy/
│       │   └── com/
│       │       └── etendoerp/
│       │           └── setup/
│       │               ├── SetupLoader.groovy                    [Loader principal]
│       │               ├── SetupApplyTemplatesTask.groovy        [Tarea principal]
│       │               ├── template/
│       │               │   ├── Template.groovy                   [Modelo de datos]
│       │               │   ├── TemplateParser.groovy             [Parser de templates]
│       │               │   ├── TemplateResolver.groovy           [Resolución de fuentes]
│       │               │   └── TemplateSection.groovy            [Enum de secciones]
│       │               └── applicator/
│       │                   ├── TemplateApplicator.groovy         [Orquestador de aplicación]
│       │                   ├── PropertyApplicator.groovy         [Aplica properties]
│       │                   ├── DependencyApplicator.groovy       [Aplica dependencies]
│       │                   └── ModuleApplicator.groovy           [Aplica modules]
│       └── resources/
│           └── templates/
│               ├── copilot.template
│               ├── base.template
│               ├── production.template
│               └── development.template
```

## 📦 Descripción de Cada Componente

### 1. SetupLoader.groovy
**Ubicación**: `src/main/groovy/com/etendoerp/setup/SetupLoader.groovy`

**Responsabilidad**: Registrar la tarea en el plugin de Etendo

**Patrón**: Sigue el mismo patrón que `CopilotLoader`, `DepsLoader`, etc.

**Integración**: Se invocará desde `EtendoPlugin.groovy`

```groovy
package com.etendoerp.setup

import org.gradle.api.Project

class SetupLoader {
    static void load(Project project) {
        project.tasks.register("setup.applyTemplates", SetupApplyTemplatesTask)
    }
}
```

---

### 2. SetupApplyTemplatesTask.groovy
**Ubicación**: `src/main/groovy/com/etendoerp/setup/SetupApplyTemplatesTask.groovy`

**Responsabilidad**: Tarea principal que maneja la lógica de ejecución

**Características**:
- Extiende `DefaultTask`
- Acepta opciones: `--template`, `--file`, `--url`
- Modo interactivo cuando no hay parámetros
- Valida y orquesta la aplicación del template

```groovy
package com.etendoerp.setup

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

class SetupApplyTemplatesTask extends DefaultTask {
    
    @Input @Optional
    String template
    
    @Input @Optional
    String file
    
    @Input @Optional
    String url
    
    @TaskAction
    void execute() {
        // 1. Resolver template desde la fuente indicada
        // 2. Aplicar template al proyecto
        // 3. Reportar resultado
    }
}
```

---

### 3. Template.groovy (Modelo de Datos)
**Ubicación**: `src/main/groovy/com/etendoerp/setup/template/Template.groovy`

**Responsabilidad**: Representa un template con sus secciones

**Estructura de Datos**:
```groovy
class Template {
    String name                           // Nombre del template
    String source                         // Origen (resource, file, url)
    Map<String, String> properties        // [properties]
    List<String> dependencies             // [dependencies]
    List<String> modules                  // [modules]
}
```

---

### 4. TemplateParser.groovy
**Ubicación**: `src/main/groovy/com/etendoerp/setup/template/TemplateParser.groovy`

**Responsabilidad**: Parsear el contenido de un template

**Lógica**:
- Lee líneas del archivo
- Identifica secciones `[properties]`, `[dependencies]`, `[modules]`
- Popula el objeto `Template`
- Valida sintaxis

```groovy
class TemplateParser {
    static Template parse(String content, String name) {
        Template template = new Template(name: name)
        String currentSection = null
        
        content.eachLine { line ->
            line = line.trim()
            
            // Detectar secciones
            if (line.startsWith('[') && line.endsWith(']')) {
                currentSection = line[1..-2]
                return
            }
            
            // Ignorar líneas vacías y comentarios
            if (line.isEmpty() || line.startsWith('#')) {
                return
            }
            
            // Procesar según sección actual
            switch(currentSection) {
                case 'properties':
                    // Parsear key=value
                    break
                case 'dependencies':
                    // Agregar línea de dependencia
                    break
                case 'modules':
                    // Agregar módulo (artifact o git)
                    break
            }
        }
        
        return template
    }
}
```

---

### 5. TemplateResolver.groovy
**Ubicación**: `src/main/groovy/com/etendoerp/setup/template/TemplateResolver.groovy`

**Responsabilidad**: Resolver la fuente del template

**Métodos**:
- `loadFromResources(String name)`: Carga desde `resources/templates/`
- `loadFromFile(String path)`: Carga desde archivo local
- `loadFromUrl(String url)`: Descarga desde URL remota
- `listAvailableTemplates()`: Lista templates en resources
- `promptUserSelection()`: Modo interactivo

```groovy
class TemplateResolver {
    
    static Template resolve(Project project, String template, String file, String url) {
        if (url) {
            return loadFromUrl(url)
        } else if (file) {
            return loadFromFile(file)
        } else if (template) {
            return loadFromResources(project, template)
        } else {
            return promptUserSelection(project)
        }
    }
    
    static Template loadFromResources(Project project, String name) {
        // Cargar desde resources/templates/
        def resourcePath = "/templates/${name}.template"
        def content = getClass().getResourceAsStream(resourcePath)?.text
        
        if (!content) {
            throw new IllegalArgumentException("Template '${name}' not found in resources")
        }
        
        return TemplateParser.parse(content, name)
    }
    
    static Template loadFromFile(String path) {
        // Cargar desde archivo local
        File file = new File(path)
        if (!file.exists()) {
            throw new FileNotFoundException("Template file not found: ${path}")
        }
        
        String name = file.name.replaceAll('\\.template$', '')
        return TemplateParser.parse(file.text, name)
    }
    
    static Template loadFromUrl(String url) {
        // Descargar desde URL remota
        String content = new URL(url).text
        String name = url.split('/').last().replaceAll('\\.template$', '')
        return TemplateParser.parse(content, name)
    }
    
    static List<String> listAvailableTemplates(Project project) {
        // Listar templates disponibles en resources
        def templatesDir = "/templates"
        // Implementar lógica para listar archivos .template
    }
    
    static Template promptUserSelection(Project project) {
        // Modo interactivo
        println "\nSelect one of the available templates:"
        
        def templates = listAvailableTemplates(project)
        templates.eachWithIndex { name, index ->
            println "${index + 1}- ${name}"
        }
        
        println "\nYou can also use:"
        println "  --template=<templateName> (template)"
        println "  --file=/path/to/template  (local file)"
        println "  --url=https://...         (remote URL)"
        println ""
        
        // Leer selección del usuario
        // Retornar template seleccionado
    }
}
```

---

### 6. TemplateApplicator.groovy (Orquestador)
**Ubicación**: `src/main/groovy/com/etendoerp/setup/applicator/TemplateApplicator.groovy`

**Responsabilidad**: Coordinar la aplicación de las tres secciones

**Flujo**:
1. Crear backup de archivos afectados
2. Aplicar properties → `PropertyApplicator`
3. Aplicar dependencies → `DependencyApplicator`
4. Aplicar modules → `ModuleApplicator`
5. Reportar cambios realizados

```groovy
class TemplateApplicator {
    
    static void apply(Project project, Template template) {
        println "\nApplying template: ${template.name}"
        
        // Crear backups
        createBackups(project)
        
        // Aplicar cada sección
        if (template.properties) {
            println "  [properties] -> gradle.properties"
            PropertyApplicator.apply(project, template.properties)
        }
        
        if (template.dependencies) {
            println "  [dependencies] -> build.gradle"
            DependencyApplicator.apply(project, template.dependencies)
        }
        
        if (template.modules) {
            println "  [modules]"
            ModuleApplicator.apply(project, template.modules)
        }
        
        println "\nTemplate '${template.name}' applied successfully"
    }
    
    private static void createBackups(Project project) {
        // Crear backup de gradle.properties
        // Crear backup de build.gradle
        def timestamp = new Date().format('yyyyMMdd_HHmmss')
        // ...
    }
}
```

---

### 7. PropertyApplicator.groovy
**Ubicación**: `src/main/groovy/com/etendoerp/setup/applicator/PropertyApplicator.groovy`

**Responsabilidad**: Aplicar properties a `gradle.properties`

**Lógica**:
- Lee `gradle.properties` actual
- Actualiza o agrega cada property del template
- Preserva comentarios existentes
- Escribe el archivo actualizado

```groovy
class PropertyApplicator {
    
    static void apply(Project project, Map<String, String> properties) {
        File propsFile = new File(project.rootDir, 'gradle.properties')
        
        if (!propsFile.exists()) {
            throw new FileNotFoundException("gradle.properties not found in project root")
        }
        
        Properties props = new Properties()
        propsFile.withInputStream { props.load(it) }
        
        properties.each { key, value ->
            props.setProperty(key, value)
            println "*** ${key}=${value}"
        }
        
        propsFile.withOutputStream { props.store(it, null) }
    }
}
```

---

### 8. DependencyApplicator.groovy
**Ubicación**: `src/main/groovy/com/etendoerp/setup/applicator/DependencyApplicator.groovy`

**Responsabilidad**: Agregar dependencies a `build.gradle`

**Lógica**:
- Localiza el bloque `dependencies { }`
- Verifica si la dependency ya existe
- Agrega o actualiza cada dependency
- Mantiene formato e indentación

```groovy
class DependencyApplicator {
    
    static void apply(Project project, List<String> dependencies) {
        File buildFile = project.file('build.gradle')
        
        if (!buildFile.exists()) {
            throw new FileNotFoundException("build.gradle not found")
        }
        
        String content = buildFile.text
        
        dependencies.each { dep ->
            if (!content.contains(dep)) {
                content = addDependencyToBlock(content, dep)
                println "*** ${dep}"
            } else {
                println "*** ${dep} (already exists)"
            }
        }
        
        buildFile.text = content
    }
    
    private static String addDependencyToBlock(String content, String dependency) {
        // Buscar el bloque dependencies { }
        // Agregar la dependencia con la indentación correcta
        // Retornar el contenido modificado
    }
}
```

---

### 9. ModuleApplicator.groovy
**Ubicación**: `src/main/groovy/com/etendoerp/setup/applicator/ModuleApplicator.groovy`

**Responsabilidad**: Procesar módulos (artifacts y git repos)

**Lógica**:
- Detecta tipo de módulo (artifact vs git)
- Para artifacts: agrega a `build.gradle` como dependency
- Para git repos: usa la lógica existente de ETP-3296
- Integra con `ModulesConfigurationLoader` si es necesario

```groovy
class ModuleApplicator {
    
    static void apply(Project project, List<String> modules) {
        modules.each { module ->
            if (module.startsWith('git::')) {
                applyGitModule(project, module)
            } else {
                applyArtifactModule(project, module)
            }
        }
    }
    
    private static void applyGitModule(Project project, String gitModule) {
        // Parsear: git::https://github.com/etendosoftware/copilot-custom.git::branch=main
        def parts = gitModule.split('::')
        def url = parts[1]
        def branch = parts[2]?.split('=')?.last() ?: 'main'
        
        println "*** git: ${url} (branch: ${branch})"
        
        // Integrar con lógica existente de clonado de repos
        // Ver: ETP-3296 y CloneDependencies.load()
    }
    
    private static void applyArtifactModule(Project project, String artifact) {
        // Formato: com.etendoerp:copilot-extras:1.0.0
        println "*** artifact: ${artifact}"
        
        // Agregar como dependency en build.gradle
        DependencyApplicator.apply(project, ["implementation '${artifact}'"])
    }
}
```

---

## 📄 Templates de Ejemplo

### copilot.template
**Ubicación**: `src/main/resources/templates/copilot.template`

```properties
[properties]
copilot.enabled=true
copilot.port=5005
agent.sync.enabled=true

[dependencies]
implementation 'com.etendoerp:copilot:1.0.0'
implementation 'com.etendoerp:agents:1.0.0'
runtimeOnly 'com.etendoerp:copilot-tools:1.0.0'

[modules]
com.etendoerp:copilot-extras:1.0.0
git::https://github.com/etendosoftware/copilot-custom.git::branch=main
```

### base.template
**Ubicación**: `src/main/resources/templates/base.template`

```properties
[properties]
bbdd.driver=org.postgresql.Driver
bbdd.url=jdbc:postgresql://localhost:5432/etendo
context.name=etendo

[dependencies]
implementation 'org.postgresql:postgresql:42.5.4'

[modules]
```

### production.template
**Ubicación**: `src/main/resources/templates/production.template`

```properties
[properties]
environment=production
log.level=WARN
cache.enabled=true

[dependencies]
implementation 'com.etendoerp:monitoring:1.0.0'

[modules]
```

### development.template
**Ubicación**: `src/main/resources/templates/development.template`

```properties
[properties]
environment=development
log.level=DEBUG
debug.enabled=true

[dependencies]
testImplementation 'junit:junit:4.13.2'

[modules]
```

---

## 🔄 Flujo de Ejecución

```
Usuario ejecuta: ./gradlew setup.applyTemplates --template=copilot

    ↓

SetupApplyTemplatesTask.execute()
    │
    ├─> TemplateResolver.resolve(project, "copilot", null, null)
    │       │
    │       ├─> Detecta que hay --template=copilot
    │       ├─> loadFromResources("copilot")
    │       └─> TemplateParser.parse(content, "copilot")
    │              │
    │              └─> Retorna Template poblado
    │
    ├─> TemplateApplicator.apply(project, template)
    │       │
    │       ├─> createBackups()
    │       │       └─> Backup de gradle.properties y build.gradle
    │       │
    │       ├─> PropertyApplicator.apply(project, template.properties)
    │       │       │
    │       │       └─> Modifica gradle.properties
    │       │           - copilot.enabled=true
    │       │           - copilot.port=5005
    │       │           - agent.sync.enabled=true
    │       │
    │       ├─> DependencyApplicator.apply(project, template.dependencies)
    │       │       │
    │       │       └─> Modifica build.gradle (dependencies)
    │       │           - implementation 'com.etendoerp:copilot:1.0.0'
    │       │           - implementation 'com.etendoerp:agents:1.0.0'
    │       │           - runtimeOnly 'com.etendoerp:copilot-tools:1.0.0'
    │       │
    │       └─> ModuleApplicator.apply(project, template.modules)
    │               │
    │               ├─> applyArtifactModule("com.etendoerp:copilot-extras:1.0.0")
    │               │
    │               └─> applyGitModule("git::https://github.com/.../copilot-custom.git::branch=main")
    │
    └─> Imprime resumen de cambios

Output:

Applying template: copilot
  [properties] -> gradle.properties
*** copilot.enabled=true
*** copilot.port=5005
*** agent.sync.enabled=true
  [dependencies] -> build.gradle
*** implementation 'com.etendoerp:copilot:1.0.0'
*** implementation 'com.etendoerp:agents:1.0.0'
*** runtimeOnly 'com.etendoerp:copilot-tools:1.0.0'
  [modules]
*** artifact: com.etendoerp:copilot-extras:1.0.0
*** git: https://github.com/etendosoftware/copilot-custom.git (branch: main)

Template 'copilot' applied successfully
```

---

## 🎯 Integración con EtendoPlugin

**Archivo**: `src/main/groovy/com/etendoerp/EtendoPlugin.groovy`

**Cambios necesarios**:

1. Agregar import:
```groovy
import com.etendoerp.setup.SetupLoader
```

2. En el método `apply()`, agregar después de las líneas existentes (~línea 68):
```groovy
SetupLoader.load(project)
```

**Ubicación exacta** (después de `GradleControllerLoader.load(project)`):
```groovy
        CopilotLoader.load(project)
        DependencyManagerLoader.load(project)
        NodeTasksLoader.load(project)
        GradleControllerLoader.load(project)
        SetupLoader.load(project)  // <-- AGREGAR AQUÍ
    }
}
```

---

## ✅ Ventajas de Esta Arquitectura

1. **Separación de responsabilidades**: Cada clase tiene un propósito único
2. **Testeable**: Cada componente puede testearse independientemente
3. **Extensible**: Fácil agregar nuevas secciones o fuentes
4. **Reutilizable**: Los applicators pueden usarse por separado
5. **Mantenible**: Sigue los patrones existentes en el proyecto
6. **Compatible**: Se integra limpiamente con la estructura actual

---

## 📝 Resumen de Archivos a Crear/Modificar

### Archivos Nuevos (13 total)

#### Código Groovy (9 archivos):
1. `src/main/groovy/com/etendoerp/setup/SetupLoader.groovy`
2. `src/main/groovy/com/etendoerp/setup/SetupApplyTemplatesTask.groovy`
3. `src/main/groovy/com/etendoerp/setup/template/Template.groovy`
4. `src/main/groovy/com/etendoerp/setup/template/TemplateParser.groovy`
5. `src/main/groovy/com/etendoerp/setup/template/TemplateResolver.groovy`
6. `src/main/groovy/com/etendoerp/setup/template/TemplateSection.groovy`
7. `src/main/groovy/com/etendoerp/setup/applicator/TemplateApplicator.groovy`
8. `src/main/groovy/com/etendoerp/setup/applicator/PropertyApplicator.groovy`
9. `src/main/groovy/com/etendoerp/setup/applicator/DependencyApplicator.groovy`
10. `src/main/groovy/com/etendoerp/setup/applicator/ModuleApplicator.groovy`

#### Templates (4 archivos):
11. `src/main/resources/templates/copilot.template`
12. `src/main/resources/templates/base.template`
13. `src/main/resources/templates/production.template`
14. `src/main/resources/templates/development.template`

### Archivos a Modificar (1 archivo):
1. `src/main/groovy/com/etendoerp/EtendoPlugin.groovy`

---

## 🧪 Testing Plan

### Unit Tests
- `TemplateParserSpec.groovy`: Test parsing de diferentes formatos
- `TemplateResolverSpec.groovy`: Test de resolución desde diferentes fuentes
- `PropertyApplicatorSpec.groovy`: Test de aplicación de properties
- `DependencyApplicatorSpec.groovy`: Test de aplicación de dependencies
- `ModuleApplicatorSpec.groovy`: Test de procesamiento de módulos

### Integration Tests
- `SetupApplyTemplatesTaskSpec.groovy`: Test end-to-end de la tarea
- Test con templates de ejemplo
- Test de modo interactivo (mock de input)
- Test de backup y restauración

---

## 🚀 Casos de Uso

### UC-1: Aplicar template bundled usando modo interactivo
```bash
$ ./gradlew setup.applyTemplates

Select one of the available templates:
1- copilot
2- base
3- production
4- development

# Usuario selecciona 1
```

### UC-2: Aplicar template bundled usando --template
```bash
$ ./gradlew setup.applyTemplates --template=copilot
```

### UC-3: Aplicar template desde URL corporativa
```bash
$ ./gradlew setup.applyTemplates --url=https://internal.company.com/templates/custom.template
```

### UC-4: Aplicar template local durante desarrollo
```bash
$ ./gradlew setup.applyTemplates --file=/path/to/my-custom.template
```

### UC-5: Aplicar template remoto sin interacción (CI/CD)
```bash
$ ./gradlew setup.applyTemplates --url=https://cdn.etendo.cloud/templates/production.template
```

---

## 📚 Referencias

- **Requisitos originales**: `Setup: Tarea Gradle para aplicar templates de configuración`
- **Issue relacionado**: [ETP-3296](https://etendoproject.atlassian.net/browse/ETP-3296) - Gestión de módulos (artifacts / git)
- **Patrones existentes**: 
  - `CopilotLoader`
  - `ModulesConfigurationLoader`
  - `DepsLoader`
  - `PublicationLoader`

---

**Documento de Implementación Técnica**  
_Proyecto Etendo - buildSrc Plugin_
