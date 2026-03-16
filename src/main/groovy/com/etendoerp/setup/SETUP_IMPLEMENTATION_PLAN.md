# Implementation Plan: `setup.applyTemplates` Task

**Date**: 16 March 2026
**Requirements**: See [SETUP_README.md](SETUP_README.md) - Setup Tasks Documentation

## File Structure

```
buildSrc/
├── src/
│   └── main/
│       ├── groovy/
│       │   └── com/
│       │       └── etendoerp/
│       │           └── setup/
│       │               ├── SetupLoader.groovy                    [Main loader]
│       │               ├── SetupApplyTemplatesTask.groovy        [Main task]
│       │               ├── template/
│       │               │   ├── Template.groovy                   [Data model]
│       │               │   ├── TemplateParser.groovy             [Template parser]
│       │               │   ├── TemplateResolver.groovy           [Source resolution]
│       │               │   ├── TemplateSection.groovy            [Section enum]
│       │               │   └── PlaceholderResolver.groovy        [Placeholder detection & prompting]
│       │               └── applicator/
│       │                   ├── TemplateApplicator.groovy         [Application orchestrator]
│       │                   ├── PropertyApplicator.groovy         [Applies properties]
│       │                   ├── DependencyApplicator.groovy       [Applies dependencies]
│       │                   └── ModuleApplicator.groovy           [Applies modules]
│       └── resources/
│           └── templates/
│               ├── local.template
│               └── server.template
```

## Component Descriptions

### 1. SetupLoader.groovy
**Location**: `src/main/groovy/com/etendoerp/setup/SetupLoader.groovy`

**Responsibility**: Register the task in the Etendo plugin.

**Pattern**: Follows the same pattern as `CopilotLoader`, `DepsLoader`, etc.

**Integration**: Invoked from `EtendoPlugin.groovy`.

```groovy
package com.etendoerp.setup

import org.gradle.api.Project

class SetupLoader {
    static void load(Project project) {
        project.tasks.register("setup.applyTemplates", SetupApplyTemplatesTask)
        project.tasks.register("setup.addModule", SetupAddModuleTask)
        project.tasks.register("setup.listModules", SetupListModulesTask)
    }
}
```

---

### 2. SetupApplyTemplatesTask.groovy
**Location**: `src/main/groovy/com/etendoerp/setup/SetupApplyTemplatesTask.groovy`

**Responsibility**: Main task that handles execution logic.

**Features**:
- Extends `DefaultTask` (group: `setup`)
- Accepts options: `--template`, `--file`, `--url`, `--force`
- Interactive mode when no parameters are provided
- Validates clean environment before applying (checks database existence via JDBC)
- After applying the template, automatically runs `./gradlew setup` to finalize changes
- `--force` bypasses the environment validation check

```groovy
class SetupApplyTemplatesTask extends DefaultTask {
    @Input @Optional @Option(option = "template", ...)
    String template

    @Input @Optional @Option(option = "file", ...)
    String file

    @Input @Optional @Option(option = "url", ...)
    String url

    @Input @Optional @Option(option = "force", ...)
    Boolean force = false

    @TaskAction
    void execute() {
        if (!force) { validateCleanEnvironment() }
        Template resolvedTemplate = TemplateResolver.resolve(project, template, file, url)
        TemplateApplicator.apply(project, resolvedTemplate)
        // Runs ./gradlew setup automatically after applying
    }
}
```

**Environment validation** (`validateCleanEnvironment`):
- Reads `bbdd.sid`, `bbdd.systemUser`, `bbdd.systemPassword`, `bbdd.url`, `bbdd.rdbms` (host, default `localhost`), and `bbdd.port` (default `5432`) from `gradle.properties`
- If `bbdd.url` is not set, constructs a JDBC URL from host, port, and SID: `jdbc:postgresql://<host>:<port>/<sid>`
- Loads the PostgreSQL driver directly from the task's classloader (bypasses `DriverManager`) to avoid classloader issues
- Attempts JDBC connection to verify whether the database already exists
- If database exists, throws an error recommending `--force`
- If `gradle.properties` does not exist, or database properties are absent, considers the environment clean
- If the PostgreSQL driver class is not found, skips the check with a warning
- If the Gradle wrapper script is not found, skips the `./gradlew setup` step and prints a warning to run it manually

---

### 3. Template.groovy (Data Model)
**Location**: `src/main/groovy/com/etendoerp/setup/template/Template.groovy`

**Responsibility**: Represents a parsed template with its sections.

**Data Structure**:
```groovy
class Template {
    String name                           // Template name
    String source                         // Origin (resources, file:<path>, url:<url>)
    Map<String, String> properties        // [properties] key-value pairs
    List<String> propertyOrder            // Ordered list of keys and comment lines
    List<String> dependencies             // [dependencies] lines
    List<String> modules                  // [modules] lines
}
```

The `propertyOrder` list preserves the order of property keys and section comment lines (e.g., `## Main-UI`, `#COPILOT`) as they appear in the template file. This allows `PropertyApplicator` to write properties in the original order with section separators.

---

### 4. TemplateParser.groovy
**Location**: `src/main/groovy/com/etendoerp/setup/template/TemplateParser.groovy`

**Responsibility**: Parse template file content into a `Template` object.

**Logic**:
- Reads lines and identifies section headers: `[properties]`, `[dependencies]`, `[modules]`
- Inside `[properties]`: parses `key=value` lines and adds keys to `propertyOrder`
- Inside `[properties]`: preserves comment lines (starting with `#`) by adding them to `propertyOrder` as section separators
- In `[dependencies]` and `[modules]`: comments are ignored
- Validates property format (`key=value`)
- Unknown sections throw `IllegalArgumentException`

```groovy
class TemplateParser {
    static Template parse(String content, String name) {
        Template template = new Template(name: name)
        TemplateSection currentSection = null

        content.eachLine { line ->
            // Detect [section] headers
            // Inside [properties]: parse key=value, preserve # comments in propertyOrder
            // Inside [dependencies]: collect dependency lines
            // Inside [modules]: collect module lines
        }

        return template
    }
}
```

---

### 5. TemplateResolver.groovy
**Location**: `src/main/groovy/com/etendoerp/setup/template/TemplateResolver.groovy`

**Responsibility**: Resolve the template source and handle placeholder resolution.

**Methods**:
- `resolve(project, template, file, url)`: Main entry point; resolves from the appropriate source, then triggers placeholder resolution if needed
- `loadFromResources(name)`: Loads from `resources/templates/<name>.template`
- `loadFromFile(path)`: Loads from a local file path
- `loadFromUrl(url)`: Downloads from a remote URL
- `listAvailableTemplates()`: Dynamically scans the `/templates/` resource directory (supports both file system and JAR protocols)
- `promptUserSelection(project)`: Interactive mode with numbered template list

**Dynamic template listing**:

`listAvailableTemplates()` scans the `/templates/` resource directory at runtime instead of returning a hardcoded list. This means adding a new `.template` file to `src/main/resources/templates/` is sufficient to make it available. The method handles both `file://` protocol (development) and `jar://` protocol (packaged builds).

**Placeholder resolution flow**:

After loading a template, `resolve()` checks if it contains placeholders via `PlaceholderResolver.hasPlaceholders()`. If placeholders exist, it:
1. Displays a "CONFIGURATION REQUIRED" banner
2. Calls `PlaceholderResolver.resolveInteractive()` to prompt for values
3. Adds GitHub credentials (`githubUser`, `githubToken`) to the properties map if provided

```groovy
class TemplateResolver {
    static Template resolve(Project project, String templateName, String filePath, String url) {
        Template template = /* load from source */

        if (PlaceholderResolver.hasPlaceholders(template)) {
            Map<String, String> userValues = PlaceholderResolver.resolveInteractive(template)
            // Map github.user -> githubUser, github.token -> githubToken
            if (userValues.containsKey('github.user')) {
                template.properties['githubUser'] = userValues['github.user']
            }
            if (userValues.containsKey('github.token')) {
                template.properties['githubToken'] = userValues['github.token']
            }
        }

        return template
    }

    static List<String> listAvailableTemplates() {
        // Scans /templates/ resource directory for *.template files
        // Supports file:// and jar:// protocols
        // Returns sorted list of template names (without .template extension)
    }
}
```

---

### 6. PlaceholderResolver.groovy
**Location**: `src/main/groovy/com/etendoerp/setup/template/PlaceholderResolver.groovy`

**Responsibility**: Detect, prompt for, and substitute placeholders in template property values.

**Placeholder format**: `{placeholder.name}` (regex: `\{([a-zA-Z0-9_.]+)\}`)

**Sensitive keys**: Only `openai.api.key` is masked during input using `Console.readPassword()`. Falls back to standard input if no console is available (e.g., IDE environments). Note: `github.token` is collected as plain text input.

**Prompt definitions**:

`LOCAL_PROMPTS` (used when `template.name == 'local'`):

| Placeholder | Prompt Message |
|-------------|----------------|
| `openai.api.key` | OpenAI API Key |

`SERVER_PROMPTS` (used for all other templates, including `server`):

| Placeholder | Prompt Message |
|-------------|----------------|
| `context.url` | Etendo ERP URL (e.g., http://clienthost/mycompanyname) |
| `openai.api.key` | OpenAI API Key |
| `github.user` | GitHub Username |
| `github.token` | GitHub Token |

**Derived values**: When the user provides `context.url`, two additional values are computed automatically:
- `context.name`: The last path segment of the URL
- `context.host`: The URL up to (but not including) the last path segment

Example: `http://myserver.com/etendo` produces `context.name=etendo`, `context.host=http://myserver.com/`

**Methods**:
- `detectPlaceholders(properties)`: Scans all property values and returns a set of placeholder names
- `hasPlaceholders(template)`: Returns `true` if any placeholders exist
- `collectUserInput(placeholders, prompts)`: Iterates through the prompts map (not the detected placeholders), displays numbered prompts (`[1/N]`, `[2/N]`, etc.), reads input, and derives values
- `deriveContextValues(contextUrl, values)`: Parses URL to extract `context.name` and `context.host`
- `substitutePlaceholders(properties, values)`: Replaces all `{placeholder}` occurrences in property values with resolved values
- `resolveInteractive(template)`: Full flow combining detect, collect, and substitute

```groovy
class PlaceholderResolver {
    private static final String PLACEHOLDER_PATTERN = /\{([a-zA-Z0-9_.]+)\}/
    private static final Set<String> SENSITIVE_KEYS = ['openai.api.key']

    static final Map<String, String> LOCAL_PROMPTS = [
        'openai.api.key': 'OpenAI API Key'
    ]

    static final Map<String, String> SERVER_PROMPTS = [
        'context.url'   : 'Etendo ERP URL (e.g., http://clienthost/mycompanyname)',
        'openai.api.key': 'OpenAI API Key',
        'github.user'   : 'GitHub Username',
        'github.token'  : 'GitHub Token'
    ]

    static Map<String, String> resolveInteractive(Template template) {
        Set<String> placeholders = detectPlaceholders(template.properties)
        Map<String, String> prompts = template.name == 'local' ? LOCAL_PROMPTS : SERVER_PROMPTS
        Map<String, String> values = collectUserInput(placeholders, prompts)
        substitutePlaceholders(template.properties, values)
        return values
    }
}
```

---

### 7. TemplateApplicator.groovy (Orchestrator)
**Location**: `src/main/groovy/com/etendoerp/setup/applicator/TemplateApplicator.groovy`

**Responsibility**: Coordinate the application of all template sections.

**Flow**:
1. Create backups of `gradle.properties` and `build.gradle` in `.template-backups/`
2. Apply properties via `PropertyApplicator` (passing `propertyOrder` for ordered output)
3. Apply dependencies via `DependencyApplicator`
4. Apply modules via `ModuleApplicator`
5. Print success message

```groovy
class TemplateApplicator {
    static void apply(Project project, Template template) {
        println "\nApplying template: ${template.name}"
        createBackups(project)

        if (template.properties) {
            println "  [properties] -> gradle.properties"
            PropertyApplicator.apply(project, template.properties, template.propertyOrder)
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
}
```

---

### 8. PropertyApplicator.groovy
**Location**: `src/main/groovy/com/etendoerp/setup/applicator/PropertyApplicator.groovy`

**Responsibility**: Apply properties to `gradle.properties`.

**Logic**:
- Reads the existing `gradle.properties` file and indexes existing keys by line number
- If `propertyOrder` is provided, writes properties and section comments in template order
- For each property: updates in-place if the key exists, appends if new
- Remaining properties not in `propertyOrder` are appended at the end
- Creates a new `gradle.properties` if one does not exist

**Sensitive value masking**:
- Keys containing `KEY`, `TOKEN`, `PASSWORD`, or `SECRET` (case-insensitive check) have their values masked in console output
- Masking format: first 4 characters + `****` + last 4 characters (e.g., `sk-Q****wxYz`)
- Values shorter than 9 characters are displayed as `****`
- Only affects console display; the full value is written to the file

```groovy
class PropertyApplicator {
    private static final List<String> SENSITIVE_KEYWORDS = ['KEY', 'TOKEN', 'PASSWORD', 'SECRET']

    static void apply(Project project, Map<String, String> properties, List<String> propertyOrder = []) {
        // Read existing gradle.properties
        // Write properties in propertyOrder with section comments
        // Append remaining properties not in propertyOrder
        // Mask sensitive values in console output
    }
}
```

---

### 9. DependencyApplicator.groovy
**Location**: `src/main/groovy/com/etendoerp/setup/applicator/DependencyApplicator.groovy`

**Responsibility**: Add dependencies to `build.gradle`.

**Logic**:
- Locates the `dependencies { }` block
- Checks if each dependency already exists
- Adds new dependencies with proper indentation
- Preserves existing formatting

---

### 10. ModuleApplicator.groovy
**Location**: `src/main/groovy/com/etendoerp/setup/applicator/ModuleApplicator.groovy`

**Responsibility**: Process modules (artifacts and git repos).

**Logic**:
- Detects module type (artifact vs git)
- For artifacts: adds to `build.gradle` as dependency
- For git repos: clones to `modules/` directory
- Integrates with existing ETP-3296 module management logic

---

## Templates

### local.template
**Location**: `src/main/resources/templates/local.template`

Designed for local development. Uses a single placeholder (`{openai.api.key}`) and also contains `{context.url}` in the SSO section, so the user is prompted for those values when the template is applied.

```properties
[properties]
## Main-UI
docker_com.etendoerp.mainui=true
etendo.classic.url=http://host.docker.internal:8080/etendo
etendo.classic.host=https://localhost:8080/etendo
next.public.app.url=https://localhost:3000/
authentication.class=com.etendoerp.etendorx.auth.SWSAuthenticationManager
ws.maxInactiveInterval=3600

#COPILOT
docker_com.etendoerp.copilot=true
copilot.host=localhost
copilot.port=5005
openai.api.key={openai.api.key}
etendo.host=http://localhost:8080/etendo
etendo.host.docker=http://host.docker.internal:8080/etendo

# SSO Login
sso.auth.type=Middleware
sso.middleware.url=https://sso.etendo.cloud
sso.middleware.redirectUri={context.url}/secureApp/LoginHandler.html
```

Key characteristics:
- Uses `LOCAL_PROMPTS` for placeholder resolution (prompts only for `openai.api.key`); `context.url` in the SSO section is also resolved via the same flow
- Uses port `8080` for all local URLs
- Does NOT include `LANGCHAIN_TRACING_V2`, `OPENAI_MODEL`, `COPILOT_WRITE_RULE`, `COPILOT_DOCKER_CONTAINER_NAME`, or `COPILOT_PROXY_URL`
- Includes an `# SSO Login` section with `sso.auth.type`, `sso.middleware.url`, and `sso.middleware.redirectUri`

### server.template
**Location**: `src/main/resources/templates/server.template`

Designed for server/production deployments. Uses placeholders that trigger interactive prompts.

```properties
[properties]
## Main-UI
docker_com.etendoerp.mainui=true
etendo.classic.url=http://host.docker.internal:80/{context.name}
etendo.classic.host={context.url}
next.public.app.url={context.host}
authentication.class=com.etendoerp.etendorx.auth.SWSAuthenticationManager
ws.maxInactiveInterval=3600

#COPILOT
docker_com.etendoerp.copilot=true
copilot.host=localhost
copilot.port=5005
openai.api.key={openai.api.key}
etendo.host=http://localhost:80/etendo
etendo.host.docker=http://host.docker.internal:80/etendo

# SSO Login
sso.auth.type=Middleware
sso.middleware.url=https://sso.etendo.cloud
sso.middleware.redirectUri={context.url}/secureApp/LoginHandler.html
```

Key characteristics:
- Uses port `80` for internal Docker URLs
- Does NOT include `LANGCHAIN_TRACING_V2`, `OPENAI_MODEL`, `COPILOT_WRITE_RULE`, `COPILOT_DOCKER_CONTAINER_NAME`, or `COPILOT_PROXY_URL`
- Placeholders: `{context.url}`, `{context.name}`, `{context.host}`, `{openai.api.key}`
- Includes an `# SSO Login` section with `sso.middleware.redirectUri={context.url}/secureApp/LoginHandler.html`
- Uses `SERVER_PROMPTS`: also prompts for `github.user` and `github.token` (saved as `githubUser`/`githubToken`)

---

## Execution Flow

```
User runs: ./gradlew setup.applyTemplates --template=server

    |
    v

SetupApplyTemplatesTask.execute()
    |
    +-> validateCleanEnvironment()
    |       |
    |       +-> If gradle.properties missing: environment is clean, continues
    |       +-> Reads bbdd.sid, bbdd.systemUser, bbdd.systemPassword,
    |       |   bbdd.url, bbdd.rdbms (host), bbdd.port from gradle.properties
    |       +-> If bbdd.sid or bbdd.systemUser absent: environment is clean, continues
    |       +-> Builds JDBC URL if bbdd.url not set
    |       +-> Loads PostgreSQL driver from task classloader
    |       +-> Attempts JDBC connection to check if database exists
    |       +-> If exists: throws error (use --force to override)
    |       +-> If connection fails (db not found): continues
    |       +-> If driver class not found: skips check with warning
    |
    +-> TemplateResolver.resolve(project, "server", null, null)
    |       |
    |       +-> loadFromResources("server")
    |       |       +-> Reads /templates/server.template
    |       |       +-> TemplateParser.parse(content, "server")
    |       |              +-> Parses [properties] with placeholders
    |       |              +-> Preserves ## Main-UI, #COPILOT, # SSO Login comments
    |       |              +-> Returns Template with propertyOrder
    |       |
    |       +-> PlaceholderResolver.hasPlaceholders(template) -> true
    |       +-> Displays "CONFIGURATION REQUIRED" banner
    |       +-> PlaceholderResolver.resolveInteractive(template)
    |       |       |
    |       |       +-> detectPlaceholders() -> {context.url, openai.api.key, context.name, context.host}
    |       |       +-> prompts = SERVER_PROMPTS (template.name != 'local')
    |       |       +-> collectUserInput() iterates SERVER_PROMPTS (4 entries):
    |       |       |     [1/4] Etendo ERP URL -> user enters URL
    |       |       |           -> derives context.name and context.host automatically
    |       |       |     [2/4] OpenAI API Key -> masked input (SENSITIVE_KEYS)
    |       |       |     [3/4] GitHub Username -> plain input
    |       |       |     [4/4] GitHub Token -> plain input
    |       |       +-> substitutePlaceholders() replaces all {placeholder}
    |       |
    |       +-> Adds githubUser, githubToken to template.properties
    |       +-> Returns fully resolved Template
    |
    +-> TemplateApplicator.apply(project, template)
    |       |
    |       +-> createBackups()
    |       |       +-> .template-backups/gradle.properties.<timestamp>
    |       |       +-> .template-backups/build.gradle.<timestamp>
    |       |
    |       +-> PropertyApplicator.apply(project, properties, propertyOrder)
    |       |       +-> Writes properties in template order
    |       |       +-> Preserves ## Main-UI, #COPILOT, # SSO Login as separators
    |       |       +-> Masks sensitive values in console output
    |       |       +-> Updates existing keys, appends new ones
    |       |
    |       +-> DependencyApplicator.apply() (if dependencies present)
    |       +-> ModuleApplicator.apply() (if modules present)
    |
    +-> Runs ./gradlew setup to finalize
    |       +-> If Gradle wrapper not found: prints warning, skips
    |
    +-> Prints success message

Output:

Applying template: server
  [properties] -> gradle.properties

  ## Main-UI
  docker_com.etendoerp.mainui=true
  etendo.classic.url=http://host.docker.internal:80/etendo
  etendo.classic.host=http://myserver.example.com/etendo
  next.public.app.url=http://myserver.example.com/

  #COPILOT
  docker_com.etendoerp.copilot=true
  openai.api.key=sk-a****wxYz
  ...

  # SSO Login
  sso.auth.type=Middleware
  sso.middleware.url=https://sso.etendo.cloud
  sso.middleware.redirectUri=http://myserver.example.com/etendo/secureApp/LoginHandler.html
  githubUser=myuser
  githubToken=ghp_****abcd

Template 'server' applied successfully
```

---

## Integration with EtendoPlugin

**File**: `src/main/groovy/com/etendoerp/EtendoPlugin.groovy`

**Changes needed**:

1. Add import:
```groovy
import com.etendoerp.setup.SetupLoader
```

2. In the `apply()` method, add after existing loaders:
```groovy
        CopilotLoader.load(project)
        DependencyManagerLoader.load(project)
        NodeTasksLoader.load(project)
        GradleControllerLoader.load(project)
        SetupLoader.load(project)  // <-- ADD HERE
    }
}
```

---

## Architecture Benefits

1. **Separation of concerns**: Each class has a single, well-defined purpose
2. **Testable**: Each component can be tested independently
3. **Extensible**: New templates are added by dropping a `.template` file in `resources/templates/`
4. **Secure**: Sensitive values are masked in console output; passwords use `Console.readPassword()`
5. **Ordered output**: `propertyOrder` preserves template structure in `gradle.properties`
6. **Placeholder support**: Templates can be parameterized for different environments
7. **Safe defaults**: Environment validation prevents accidental reconfiguration
8. **Compatible**: Follows existing patterns in the project (loaders, tasks)

---

## File Summary

### Source Files (11 total)

#### Groovy Classes (10 files):
1. `src/main/groovy/com/etendoerp/setup/SetupLoader.groovy`
2. `src/main/groovy/com/etendoerp/setup/SetupApplyTemplatesTask.groovy`
3. `src/main/groovy/com/etendoerp/setup/template/Template.groovy`
4. `src/main/groovy/com/etendoerp/setup/template/TemplateParser.groovy`
5. `src/main/groovy/com/etendoerp/setup/template/TemplateResolver.groovy`
6. `src/main/groovy/com/etendoerp/setup/template/TemplateSection.groovy`
7. `src/main/groovy/com/etendoerp/setup/template/PlaceholderResolver.groovy`
8. `src/main/groovy/com/etendoerp/setup/applicator/TemplateApplicator.groovy`
9. `src/main/groovy/com/etendoerp/setup/applicator/PropertyApplicator.groovy`
10. `src/main/groovy/com/etendoerp/setup/applicator/DependencyApplicator.groovy`
11. `src/main/groovy/com/etendoerp/setup/applicator/ModuleApplicator.groovy`

#### Templates (2 files):
1. `src/main/resources/templates/local.template`
2. `src/main/resources/templates/server.template`

### File to Modify (1 file):
1. `src/main/groovy/com/etendoerp/EtendoPlugin.groovy`

---

## Testing Plan

### Unit Tests
- `TemplateParserSpec.groovy`: Test parsing of sections, comments, and property order
- `TemplateResolverSpec.groovy`: Test resolution from resources, file, and URL sources
- `PlaceholderResolverSpec.groovy`: Test placeholder detection, derivation of context values, substitution
- `PropertyApplicatorSpec.groovy`: Test property application, ordered writing, sensitive value masking
- `DependencyApplicatorSpec.groovy`: Test dependency addition and deduplication
- `ModuleApplicatorSpec.groovy`: Test artifact and git module processing

### Integration Tests
- `SetupApplyTemplatesTaskSpec.groovy`: End-to-end test of the task
- Test with `local` template (no interaction)
- Test with `server` template (mocked input for placeholders)
- Test environment validation (clean vs existing database)
- Test `--force` flag behavior
- Test backup creation and file content

---

## Use Cases

### UC-1: Apply local template for development
```bash
$ ./gradlew setup.applyTemplates --template=local
# Prompts for: OpenAI API Key (masked) and context URL (for SSO redirect URI)
```

### UC-2: Apply server template interactively
```bash
$ ./gradlew setup.applyTemplates --template=server
# Prompts for: context URL, OpenAI API key, GitHub user, GitHub token
```

### UC-3: Select template interactively
```bash
$ ./gradlew setup.applyTemplates
# Shows numbered list: 1) local  2) server
# User selects a number
```

### UC-4: Apply custom template from file
```bash
$ ./gradlew setup.applyTemplates --file=/path/to/my-custom.template
```

### UC-5: Apply template from URL (CI/CD)
```bash
$ ./gradlew setup.applyTemplates --url=https://cdn.etendo.cloud/templates/ci.template
```

### UC-6: Force apply on existing environment
```bash
$ ./gradlew setup.applyTemplates --template=local --force
```

---

## References

- **Requirements**: `Setup: Tarea Gradle para aplicar templates de configuracion`
- **Related issue**: [ETP-3291](https://etendoproject.atlassian.net/browse/ETP-3291) - Template infrastructure
- **Related issue**: [ETP-3296](https://etendoproject.atlassian.net/browse/ETP-3296) - Module management
- **Existing patterns**:
  - `CopilotLoader`
  - `ModulesConfigurationLoader`
  - `DepsLoader`
  - `PublicationLoader`

---

**Technical Implementation Document**
_Etendo Project - buildSrc Plugin_
