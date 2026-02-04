# Setup Tasks Documentation

## Overview

The Setup tasks provide a comprehensive set of tools for managing your Etendo project configuration. These tasks allow you to apply configuration templates, manage individual modules, and view installed components.

**Available Tasks:**
- `setup.applyTemplates` - Apply complete configuration templates
- `setup.addModule` - Add individual modules (artifacts or git repositories)
- `setup.listModules` - List all installed modules

---

## Table of Contents

1. [setup.applyTemplates](#setupapplytemplates)
2. [setup.addModule](#setupaddmodule)
3. [setup.listModules](#setuplistmodules)
4. [Template Format](#template-format)
5. [Module Management](#module-management)
6. [CI/CD Usage](#cicd-usage)
7. [Troubleshooting](#troubleshooting)

---

## setup.applyTemplates

Apply complete configuration templates to your Etendo project. Templates are predefined configuration files that contain properties, dependencies, and modules.

### Usage Modes

The `setup.applyTemplates` task supports four different usage modes:

### 1. Interactive Mode (No Parameters)

```bash
./gradlew setup.applyTemplates
```

This will display a list of available templates and prompt you to select one:

```
Select one of the available templates:
1- copilot
2- base
3- production
4- development

You can also use:
  --template=<templateName> (template)
  --file=/path/to/template  (local file)
  --url=https://...         (remote URL)

Enter your selection (1-4):
```

### 2. Using Bundled Templates

```bash
./gradlew setup.applyTemplates --template=<templateName>
```

**Available bundled templates:**
- `copilot` - Configures Etendo Copilot integration
- `base` - Basic PostgreSQL database configuration
- `production` - Production environment settings
- `development` - Development environment settings

**Example:**
```bash
./gradlew setup.applyTemplates --template=copilot
```

### 3. Using Local Template File

```bash
./gradlew setup.applyTemplates --file=/path/to/custom.template
```

**Example:**
```bash
./gradlew setup.applyTemplates --file=/home/user/my-template.template
```

### 4. Using Remote Template URL

```bash
./gradlew setup.applyTemplates --url=https://example.com/templates/custom.template
```

**Example:**
```bash
./gradlew setup.applyTemplates --url=https://cdn.etendo.cloud/templates/production.template
```

## Template Format

Templates use a simple INI-like format with three sections:

```properties
[properties]
key=value
another.key=another.value

[dependencies]
implementation 'group:artifact:version'
testImplementation 'group:artifact:version'

[modules]
group:artifact:version
git::https://github.com/user/repo.git::branch=main
```

### Template Sections

#### [properties]
Properties are added or updated in `gradle.properties`:
- If a property already exists, it will be updated
- If it doesn't exist, it will be added

#### [dependencies]
Dependencies are added to the `dependencies` block in `build.gradle`:
- If a dependency already exists, it will be skipped
- New dependencies are added with proper indentation

#### [modules]
Modules can be:
- **Artifacts**: `group:artifact:version` (added to `artifacts.list.COMPILATION.gradle`)
- **Git repositories**: `git::<url>::branch=<branch>` (cloned to modules directory)

### Example Templates

### Copilot Template
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

### Development Template
```properties
[properties]
environment=development
log.level=DEBUG
debug.enabled=true
hot.reload=true

[dependencies]
testImplementation 'junit:junit:4.13.2'

[modules]
```

### Example Output

When applying a template, you'll see output like:

```
Applying template: development
  [properties] -> gradle.properties
*** environment=development
*** log.level=DEBUG
*** debug.enabled=true
*** hot.reload=true
  [dependencies] -> build.gradle
*** testImplementation 'junit:junit:4.13.2'
  [modules]
*** artifact: com.etendoerp:copilot-extras:1.0.0

Template 'development' applied successfully
```

### Backups

Before applying any template, backups of the following files are created in `.template-backups/`:
- `gradle.properties.<timestamp>`
- `build.gradle.<timestamp>`

---

## setup.addModule

Add individual modules to your Etendo project without applying a complete template. Supports both Maven/Gradle artifacts and Git repositories.

### Usage

#### Add Maven/Gradle Artifact

```bash
./gradlew setup.addModule --artifact=<group>:<artifact>:<version>
```

**Example:**
```bash
./gradlew setup.addModule --artifact=com.etendoerp:warehouse:2.1.0
```

This will:
- Add the artifact to `artifacts.list.COMPILATION.gradle`
- Skip if the artifact already exists

#### Add Git Repository

```bash
./gradlew setup.addModule --git=<url> --branch=<branch>
```

**Parameters:**
- `--git`: Git repository URL (HTTPS or SSH)
- `--branch`: Branch name (optional, defaults to 'main', 'master', or repository default)

**Examples:**

```bash
# With specific branch
./gradlew setup.addModule --git=https://github.com/etendosoftware/custom-module.git --branch=develop

# Using default branch
./gradlew setup.addModule --git=https://github.com/etendosoftware/custom-module.git

# SSH URL
./gradlew setup.addModule --git=git@github.com:company/private-module.git --branch=main
```

This will:
- Clone the repository into `modules/<module-name>`
- Skip if the module directory already exists
- Automatically detect the default branch if not specified

### Example Output

#### Adding Artifact (Success)
```
Adding module: com.etendoerp:reporting:1.2.0
*** artifact: com.etendoerp:reporting:1.2.0
*** added to artifacts.list.COMPILATION.gradle

Module added successfully!
```

#### Adding Artifact (Already Exists)
```
Adding module: com.etendoerp:copilot:1.0.0
*** artifact: com.etendoerp:copilot:1.0.0
*** (already in artifacts list)

Module already exists. Skipped.
```

#### Adding Git Module (Success)
```
Adding git module: https://github.com/etendosoftware/custom-module.git
*** git: https://github.com/etendosoftware/custom-module.git (branch: develop)
*** cloned to modules/custom-module (branch: develop)

Module cloned successfully!
```

#### Adding Git Module (Already Exists)
```
Adding git module: https://github.com/etendosoftware/custom-module.git
*** git: https://github.com/etendosoftware/custom-module.git (already cloned in modules/custom-module)

Module already exists. Skipped.
```

### Validation

The task validates:
- Only one option (`--artifact` OR `--git`) must be provided
- Artifact format must be `group:artifact:version`
- Git URL must be accessible
- Branch must exist (if specified)

---

## setup.listModules

List all installed modules in your Etendo project, including both Maven/Gradle artifacts and Git repositories.

### Usage

#### Default Mode (Table Format)

```bash
./gradlew setup.listModules
```

#### JSON Format

```bash
./gradlew setup.listModules --format=json
```

### Example Output

#### Table Format (Default)

```
=== Installed Modules ===

Artifacts (from artifacts.list.COMPILATION.gradle):
  1. com.etendoerp:copilot:1.0.0
  2. com.etendoerp:tomcat:1.0.0
  3. com.etendoerp:warehouse:2.1.0
  4. com.etendoerp:reporting:1.2.0

Git Modules (from modules/ directory):
  1. copilot-tools
     - Path: modules/copilot-tools
     - Branch: main
     - Remote: https://github.com/etendosoftware/com.etendoerp.task.git
  
  2. custom-module
     - Path: modules/custom-module
     - Branch: develop
     - Remote: https://github.com/company/custom-module.git
  
  3. warehouse-extras
     - Path: modules/warehouse-extras
     - Branch: feature/new-ui
     - Remote: git@github.com:etendosoftware/warehouse-extras.git

Total: 4 artifacts, 3 git modules
```

#### JSON Format

```json
{
  "artifacts": [
    "com.etendoerp:copilot:1.0.0",
    "com.etendoerp:tomcat:1.0.0",
    "com.etendoerp:warehouse:2.1.0",
    "com.etendoerp:reporting:1.2.0"
  ],
  "gitModules": [
    {
      "name": "copilot-tools",
      "path": "modules/copilot-tools",
      "branch": "main",
      "remoteUrl": "https://github.com/etendosoftware/com.etendoerp.task.git"
    },
    {
      "name": "custom-module",
      "path": "modules/custom-module",
      "branch": "develop",
      "remoteUrl": "https://github.com/company/custom-module.git"
    },
    {
      "name": "warehouse-extras",
      "path": "modules/warehouse-extras",
      "branch": "feature/new-ui",
      "remoteUrl": "git@github.com:etendosoftware/warehouse-extras.git"
    }
  ],
  "summary": {
    "totalArtifacts": 4,
    "totalGitModules": 3
  }
}
```

### Information Displayed

**For Artifacts:**
- Coordinate (`group:artifact:version`)
- Source: `artifacts.list.COMPILATION.gradle`

**For Git Modules:**
- Module name
- Local path
- Current branch
- Remote repository URL

---

## Template Format

Templates use a simple INI-like format with three sections. See [Template Sections](#template-sections) above for details.

### Creating Custom Templates

To create your own template:

1. Create a file with `.template` extension
2. Add the three sections: `[properties]`, `[dependencies]`, `[modules]`
3. Use the template with `--file` or `--url` option

**Example custom template:**

```properties
# My Custom Template
# Description of what this template does

[properties]
my.custom.property=value
another.property=123

[dependencies]
implementation 'com.example:my-library:1.0.0'

[modules]
com.example:my-module:1.0.0
git::https://github.com/company/custom-module.git::branch=develop
```

---

## Module Management

### Module Types

Etendo supports two types of modules:

#### 1. Maven/Gradle Artifacts
- Format: `group:artifact:version`
- Stored in: `artifacts.list.COMPILATION.gradle`
- Examples:
  ```
  com.etendoerp:copilot:1.0.0
  com.etendoerp:warehouse:2.1.0
  com.etendoerp.localization.spain:1.5.0
  ```

#### 2. Git Repositories
- Format: `git::<url>::branch=<branch>`
- Stored in: `modules/<module-name>/` directory
- Examples:
  ```
  git::https://github.com/etendosoftware/copilot.git::branch=develop
  git::https://github.com/company/warehouse.git::branch=feature/new-ui
  git::git@github.com:company/private-module.git::branch=main
  ```

### Module Operations

| Operation | Task | Example |
|-----------|------|---------|
| Add artifact | `setup.addModule` | `./gradlew setup.addModule --artifact=group:artifact:version` |
| Add git module | `setup.addModule` | `./gradlew setup.addModule --git=url --branch=branch` |
| List all modules | `setup.listModules` | `./gradlew setup.listModules` |
| Apply template with modules | `setup.applyTemplates` | `./gradlew setup.applyTemplates --template=copilot` |

### Best Practices

1. **Use templates for environment setup**: Define complete environment configurations in templates
2. **Use addModule for incremental changes**: Add individual modules during development
3. **Check existing modules first**: Use `setup.listModules` before adding new modules
4. **Git modules for development**: Use git modules when you need to modify the module code
5. **Artifacts for production**: Use artifacts for stable, versioned dependencies

### Workflow Example

```bash
# 1. Start with a base template
./gradlew setup.applyTemplates --template=base

# 2. Add development-specific modules
./gradlew setup.addModule --git=https://github.com/company/dev-tools.git --branch=develop

# 3. Add additional artifacts as needed
./gradlew setup.addModule --artifact=com.etendoerp:reporting:1.2.0

# 4. Verify installation
./gradlew setup.listModules
```

---

## CI/CD Usage

For automated environments, use the non-interactive modes:

### Apply Templates in CI/CD

```bash
# Apply template from URL
./gradlew setup.applyTemplates --url=https://internal.company.com/templates/ci.template

# Apply bundled template
./gradlew setup.applyTemplates --template=production
```

### Add Modules in CI/CD

```bash
# Add artifact
./gradlew setup.addModule --artifact=com.etendoerp:monitoring:1.0.0

# Add git module
./gradlew setup.addModule --git=https://github.com/company/ci-tools.git --branch=main
```

### List Modules in CI/CD

```bash
# Get JSON output for parsing
./gradlew setup.listModules --format=json > modules.json

# Use in pipeline scripts
MODULES=$(./gradlew -q setup.listModules --format=json)
echo "$MODULES" | jq '.artifacts | length'
```

---

## Troubleshooting

### setup.applyTemplates Issues

**Template not found**

If you get an error like "Template 'xyz' not found in resources", ensure:
- The template name is correct (case-sensitive)
- You're using one of the bundled templates: copilot, base, production, development

**File not found**

For file-based templates:
- Verify the file path is correct and absolute
- Ensure the file has proper read permissions
- Check that the file extension is `.template`

**URL connection failed**

For URL-based templates:
- Verify the URL is accessible
- Check your internet connection
- Ensure the URL returns a valid template file

### setup.addModule Issues

**Invalid artifact format**

Ensure artifact follows the format: `group:artifact:version`

```bash
# ✅ Correct
./gradlew setup.addModule --artifact=com.etendoerp:copilot:1.0.0

# ❌ Incorrect
./gradlew setup.addModule --artifact=copilot:1.0.0  # missing group
```

**Git clone failed**

- Verify git is installed: `git --version`
- Ensure the repository URL is accessible
- Check branch exists: `git ls-remote --heads <url>`
- For SSH URLs, verify SSH keys are configured

**Module already exists**

The task will skip modules that already exist and show a message. This is expected behavior.

### setup.listModules Issues

**No modules found**

If no modules are listed:
- Check `artifacts.list.COMPILATION.gradle` exists
- Verify `modules/` directory exists
- Ensure modules were added using proper commands

**Git information not showing**

For git modules, ensure:
- The directory is a valid git repository
- `.git` directory exists in the module folder
- Git is installed and accessible

---

## See Also

- [SETUP_IMPLEMENTATION_PLAN.md](SETUP_IMPLEMENTATION_PLAN.md) - Technical implementation details
- [ETP-3291](https://etendoproject.atlassian.net/browse/ETP-3291) - Template infrastructure
- [ETP-3296](https://etendoproject.atlassian.net/browse/ETP-3296) - Module management feature

---

**Setup Tasks Documentation**  
_Etendo Setup Package - buildSrc Plugin_  
_Last Updated: 3 de febrero de 2026_
