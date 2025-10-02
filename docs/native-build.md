# Native Build Guide

This guide covers building native executables for the Linear-Jira Sync tool using GraalVM Native Image.

## Prerequisites

- **GraalVM 21+** or **Mandrel 21+** (GraalVM community distribution)
- **Native Image component** installed
- **Platform-specific build tools:**
  - **Linux**: `gcc`, `glibc-devel`, `zlib-devel`
  - **macOS**: Xcode Command Line Tools
  - **Windows**: Visual Studio Build Tools

## Installation

### Option 1: SDKMAN (Recommended for macOS/Linux)

```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install GraalVM
sdk install java 25-graal
sdk use java 25-graal

# Verify installation
java -version
native-image --version
```

### Option 2: Homebrew (macOS)

```bash
# Install GraalVM
brew install --cask graalvm-jdk

# Add to PATH (add to ~/.zshrc or ~/.bash_profile)
export PATH="/Library/Java/JavaVirtualMachines/graalvm-jdk-21/Contents/Home/bin:$PATH"

# Verify installation
java -version
native-image --version
```

### Option 3: Manual Installation

1. **Download GraalVM:**
   - [GraalVM Downloads](https://www.graalvm.org/downloads/)
   - Or [Mandrel Releases](https://github.com/graalvm/mandrel/releases)

2. **Extract and configure:**
   ```bash
   # Extract to preferred location
   tar -xzf graalvm-jdk-21_*.tar.gz

   # Set JAVA_HOME
   export JAVA_HOME=/path/to/graalvm-jdk-21
   export PATH=$JAVA_HOME/bin:$PATH
   ```

3. **Verify installation:**
   ```bash
   java -version
   native-image --version
   ```

## Platform-Specific Setup

### macOS

Install Xcode Command Line Tools:
```bash
xcode-select --install
```

### Linux (Ubuntu/Debian)

```bash
sudo apt-get update
sudo apt-get install build-essential zlib1g-dev
```

### Linux (RHEL/CentOS/Fedora)

```bash
sudo dnf install gcc glibc-devel zlib-devel
```

### Windows

1. Install [Visual Studio Build Tools](https://visualstudio.microsoft.com/downloads/)
2. Select "Desktop development with C++" workload
3. Run native build from "x64 Native Tools Command Prompt"

## Building Native Executable

### Basic Build

```bash
# Build native executable
./mvnw package -Dnative

# Run the native binary
./target/linear-jira-sync-1.0.0-SNAPSHOT-runner sync --help
```

### Build with Custom Configuration

```bash
# Build with specific GraalVM options
./mvnw package -Dnative \
  -Dquarkus.native.additional-build-args="-H:+ReportExceptionStackTraces"

# Build with container (if Docker/Podman available)
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

## Benefits of Native Executables

- **Fast startup**: Milliseconds instead of seconds
- **Low memory footprint**: ~10-50MB vs 100-200MB for JVM
- **No JVM required**: Self-contained executable
- **Ideal for**: CLI tools, serverless, containers

## Limitations

- **Longer build time**: 2-5 minutes vs seconds for JVM
- **Reflection restrictions**: Some Java features require configuration
- **Dynamic class loading**: Limited support
- **Size**: Binary is larger (~50-80MB) than JAR

## Troubleshooting

### Build Fails with "native-image not found"

```bash
# Verify GraalVM is active
java -version  # Should show "GraalVM"

# Check native-image
which native-image
native-image --version
```

### Out of Memory During Build

```bash
# Increase build memory
./mvnw package -Dnative -Dquarkus.native.native-image-xmx=8g
```

### Missing Dependencies on Linux

```bash
# Ubuntu/Debian
sudo apt-get install build-essential libz-dev

# RHEL/Fedora
sudo dnf install gcc glibc-devel zlib-devel libstdc++-static
```

### Reflection Errors at Runtime

If you encounter reflection errors, add configuration in `src/main/resources/META-INF/native-image/reflect-config.json`:

```json
[
  {
    "name": "com.example.YourClass",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  }
]
```

## Container-Based Build

If you don't want to install GraalVM locally:

```bash
# Build using container image (Docker/Podman required)
./mvnw package -Dnative -Dquarkus.native.container-build=true

# Specify builder image
./mvnw package -Dnative \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21
```

## Performance Comparison

| Metric | JVM (JAR) | Native |
|--------|-----------|--------|
| Startup time | ~1-2s | ~0.01s |
| Memory (idle) | ~100MB | ~20MB |
| Memory (active) | ~200MB | ~50MB |
| Build time | ~10s | ~3min |
| Binary size | ~15MB | ~60MB |

## Additional Resources

- [Quarkus Native Build Guide](https://quarkus.io/guides/building-native-image)
- [GraalVM Native Image Documentation](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Quarkus Native Reference](https://quarkus.io/guides/native-reference)
- [GraalVM Native Image Tips](https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildConfiguration/)
