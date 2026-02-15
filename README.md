# SNT Java Version Bridge

A lightweight Java 8-compiled JAR that provides a graceful upgrade path for
[SNT](https://github.com/morphonets/SNT) users on legacy Fiji installations.

## Problem

SNT now requires Java 21, but many Fiji installations still run Java 8. The
[Fiji Updater](https://imagej.net/plugins/updater) has no mechanism (at least
for now)to serve different JARs based on JVM version. It always delivers the
latest artifact. When Java 8 users update, they pull the Java 21-compiled SNT
JAR and encounter a cryptic `UnsupportedClassVersionError` the moment they
try to use any SNT command.

## Solution

This bridge JAR is compiled for Java 8 and registered as a
[SciJava Service](https://javadoc.scijava.org/SciJava/org/scijava/service/Service.html).
During Fiji's context initialization, all Service `initialize()` methods are
called, and since this JAR targets Java 8, it loads successfully on any Fiji.

The bridge checks the running JVM version:

- **Java 21+:** Does nothing. SNT loads and works normally.
- **Java 8 (or any version < 21):** Displays a dialog explaining that SNT
  requires Fiji-Latest (Java 21), with clickable links to the download page and
  the Image.sc forum.

SNT menu entries may (will!?) still appear, but clicking them on Java 8 will fail.
The startup dialog ensures users understand _why_ before they encounter any error.

## Implementation

```
  Fiji startup (Java 8)          Fiji startup (Java 21)
           │                              │
      Context init                   Context init
           │                              │
     Load Services                  Load Services
           │                              │
  ┌──────────-───────┐          ┌──────────────-───┐
  │ JavaVersionBridge│          │ JavaVersionBridge│
  │ (Java 8 bytecode)│          │ (Java 8 bytecode)│
  │ ✓ Loads fine     │          │ ✓ Loads fine     │
  │                  │          │                  │
  │ Detects Java 8   │          │ Detects Java 21  │
  │ → Shows dialog   │          │ → Returns (noop) │
  └───────────────-──┘          └───────────────-──┘
           │                              │
    User clicks SNT                 User clicks SNT
           │                              │
    UnsupportedClass-               SNT loads and
    VersionError (but               works normally
    user already knows
    why from the dialog)
```

## Deployment

1. Build this project: `mvn clean package`
2. Upload `snt-bridge-<version>.jar` to the Neuroanatomy update site
   alongside the Java 21-compiled SNT JARs
3. Both JARs live on the same update site — the bridge handles version
   detection at runtime

**NB:** Do not change the compiler source/target from 1.8. The whole
point of this JAR is that it must load on Java 8!

## Updating the Required Version

If a future SNT release raises the minimum to, say, Java 25, edit the
`REQUIRED_JAVA_VERSION` constant in `JavaVersionBridge.java`
