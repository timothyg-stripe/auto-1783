# https://github.com/google/auto/issues/1783 reproduction

Error messages all come from an M1 MacBook.

## Act 1: Original failure

```sh
# Prebuild direct Turbine to avoid circular dependency issues.
bazel build //turbine:turbine_direct_graal
cp -f bazel-bin/turbine/turbine_direct_graal toolchains/turbine_direct_graal_precompiled

bazel build --config=use_prebuilt //java/test:my_binary
```

Error:

```
ERROR: auto-1783/java/test/BUILD.bazel:16:13: Compiling Java headers java/test/libmy_library-hjar.jar (1 source file) and running annotation processors (AutoValueProcessor) failed: (Exit 1): turbine_direct_graal_precompiled failed: error executing JavacTurbine command (from target //java/test:my_library) toolchains/turbine_direct_graal_precompiled '-Dturbine.ctSymPath=external/remotejdk21_macos_aarch64/lib/ct.sym' @bazel-out/darwin_arm64-fastbuild/bin/java/test/libmy_library-hjar.jar-0.params ... (remaining 1 argument skipped)

Use --sandbox_debug to see verbose messages from the sandbox and retain the sandbox build root for debugging
<>: error: An exception occurred in com.google.auto.value.processor.AutoValueProcessor:
java.lang.NoClassDefFoundError: Could not initialize class com.google.auto.value.processor.AutoValueTemplateVars
	at com.google.auto.value.processor.AutoValueProcessor.processType(AutoValueProcessor.java:245)
	at com.google.auto.value.processor.AutoValueishProcessor.process(AutoValueishProcessor.java:442)
	at com.google.turbine.binder.Processing.process(Processing.java:180)
	at com.google.turbine.binder.Binder.bind(Binder.java:120)
	at com.google.turbine.binder.Binder.bind(Binder.java:94)
	at com.google.turbine.main.Main.bind(Main.java:270)
	at com.google.turbine.main.Main.fallback(Main.java:245)
	at com.google.turbine.main.Main.compile(Main.java:172)
	at com.google.turbine.main.Main.compile(Main.java:133)
	at com.google.turbine.main.Main.main(Main.java:89)
	at java.base@22/java.lang.invoke.LambdaForm$DMH/sa346b79c.invokeStaticInit(LambdaForm$DMH
```

Let's try to get a better error message – for some reason, GraalVM doesn't give great class initialization exception messages.

## Act 2: Better error message

```sh
git switch add-debug-options
git show
```
```diff
diff --git a/turbine/BUILD.bazel b/turbine/BUILD.bazel
index 5df1d2f..f19ccdd 100644
--- a/turbine/BUILD.bazel
+++ b/turbine/BUILD.bazel
@@ -35,7 +35,7 @@ native_image(
         # See :turbine_benchmark for the benchmark script used.
         "-R:MinHeapSize=512m",
         # For debugging:
-        # "--initialize-at-build-time=com.google.auto.value.processor.AutoValueTemplateVars,autovalue.shaded.com.google.escapevelocity,autovalue.shaded.com.google.common.collect,autovalue.shaded.com.google.common.base",
+        "--initialize-at-build-time=com.google.auto.value.processor.AutoValueTemplateVars,autovalue.shaded.com.google.escapevelocity,autovalue.shaded.com.google.common.collect,autovalue.shaded.com.google.common.base",
     ] + select({
         "@platforms//cpu:x86_64": [
             # Graal's default settings result in executables that aren't sufficiently compatible for
```

Essentially, we force Turbine to initialize a bunch of classes at build time, which somehow gives better error messages at runtime.

```sh
# Prebuild direct Turbine to avoid circular dependency issues.
bazel build //turbine:turbine_direct_graal
cp -f bazel-bin/turbine/turbine_direct_graal toolchains/turbine_direct_graal_precompiled

bazel build --config=use_prebuilt //java/test:my_binary
```

Error message:

```
ERROR: auto-1783/java/test/BUILD.bazel:16:13: Compiling Java headers java/test/libmy_library-hjar.jar (1 source file) and running annotation processors (AutoValueProcessor) failed: (Exit 1): turbine_direct_graal_precompiled failed: error executing JavacTurbine command (from target //java/test:my_library) toolchains/turbine_direct_graal_precompiled '-Dturbine.ctSymPath=external/remotejdk21_macos_aarch64/lib/ct.sym' @bazel-out/darwin_arm64-fastbuild/bin/java/test/libmy_library-hjar.jar-0.params ... (remaining 1 argument skipped)

Use --sandbox_debug to see verbose messages from the sandbox and retain the sandbox build root for debugging
<>: error: An exception occurred in com.google.auto.value.processor.AutoValueProcessor:
java.lang.AssertionError: Template search logic fails for: resource:/com/google/auto/value/processor/equalshashcode.vm
	at com.google.auto.value.processor.TemplateVars.readerFromUrl(TemplateVars.java:148)
	at autovalue.shaded.com.google.escapevelocity.Template.parseFrom(Template.java:146)
	at autovalue.shaded.com.google.escapevelocity.ParseNode.render(ParseNode.java:62)
	at autovalue.shaded.com.google.escapevelocity.Node$Cons.render(Node.java:91)
	at autovalue.shaded.com.google.escapevelocity.Template.render(Template.java:189)
	at autovalue.shaded.com.google.escapevelocity.Template.evaluate(Template.java:179)
	at com.google.auto.value.processor.TemplateVars.toText(TemplateVars.java:100)
	at com.google.auto.value.processor.AutoValueProcessor.processType(AutoValueProcessor.java:274)
	at com.google.auto.value.processor.AutoValueishProcessor.process(AutoValueishProcessor.java:442)
	at com.google.turbine.binder.Processing.process(Processing.java:180)
	at com.google.turbine.binder.Binder.bind(Binder.java:120)
	at com.google.turbine.binder.Binder.bind(Binder.java:94)
	at com.google.turbine.main.Main.bind(Main.java:270)
	at com.google.turbine.main.Main.fallback(Main.java:245)
	at com.google.turbine.main.Main.compile(Main.java:172)
	at com.google.turbine.main.Main.compile(Main.java:133)
	at com.google.turbine.main.Main.main(Main.java:89)
	at java.base@22/java.lang.invoke.LambdaForm$DMH/sa346b79c.invokeStaticInit(LambdaForm$DMH)
```

## Act 3: Downgrade

When we downgrade to 1.10.3, we find a different error message that demonstrates the .vm resource was loaded correctly, but evaluating it fails due to missing reflection classes.

```sh
git switch downgrade

# Prebuild direct Turbine to avoid circular dependency issues.
bazel build //turbine:turbine_direct_graal
cp -f bazel-bin/turbine/turbine_direct_graal toolchains/turbine_direct_graal_precompiled

bazel build --config=use_prebuilt //java/test:my_binary
```
```
ERROR: auto-1783/java/test/BUILD.bazel:16:13: Compiling Java headers java/test/libmy_library-hjar.jar (1 source file) and running annotation processors (AutoValueProcessor) failed: (Exit 1): turbine_direct_graal_precompiled failed: error executing JavacTurbine command (from target //java/test:my_library) toolchains/turbine_direct_graal_precompiled '-Dturbine.ctSymPath=external/remotejdk21_macos_aarch64/lib/ct.sym' @bazel-out/darwin_arm64-fastbuild/bin/java/test/libmy_library-hjar.jar-0.params ... (remaining 1 argument skipped)

Use --sandbox_debug to see verbose messages from the sandbox and retain the sandbox build root for debugging
java/test/MyLibrary.java:6: error: [AutoValueException] @AutoValue processor threw an exception: autovalue.shaded.com.google.escapevelocity.EvaluationException: In expression on line 31 of autovalue.vm: In $pkg.empty: empty does not correspond to a public getter of test, a java.lang.String
	at autovalue.shaded.com.google.escapevelocity.Node.evaluationException(Node.java:55)
	at autovalue.shaded.com.google.escapevelocity.ReferenceNode.evaluationExceptionInThis(ReferenceNode.java:50)
	at autovalue.shaded.com.google.escapevelocity.ReferenceNode$MemberReferenceNode.evaluate(ReferenceNode.java:144)
	at autovalue.shaded.com.google.escapevelocity.ExpressionNode.isTrue(ExpressionNode.java:109)
	at autovalue.shaded.com.google.escapevelocity.ExpressionNode$NotExpressionNode.evaluate(ExpressionNode.java:315)
	at autovalue.shaded.com.google.escapevelocity.ExpressionNode.isTrue(ExpressionNode.java:109)
	at autovalue.shaded.com.google.escapevelocity.DirectiveNode$IfNode.render(DirectiveNode.java:102)
	at autovalue.shaded.com.google.escapevelocity.Node$Cons.render(Node.java:91)
	at autovalue.shaded.com.google.escapevelocity.Template.render(Template.java:189)
	at autovalue.shaded.com.google.escapevelocity.Template.evaluate(Template.java:179)
	at com.google.auto.value.processor.TemplateVars.toText(TemplateVars.java:99)
	at com.google.auto.value.processor.AutoValueProcessor.processType(AutoValueProcessor.java:274)
	at com.google.auto.value.processor.AutoValueishProcessor.process(AutoValueishProcessor.java:441)
	at com.google.turbine.binder.Processing.process(Processing.java:180)
	at com.google.turbine.binder.Binder.bind(Binder.java:120)
	at com.google.turbine.binder.Binder.bind(Binder.java:94)
	at com.google.turbine.main.Main.bind(Main.java:270)
	at com.google.turbine.main.Main.fallback(Main.java:245)
	at com.google.turbine.main.Main.compile(Main.java:172)
	at com.google.turbine.main.Main.compile(Main.java:133)
	at com.google.turbine.main.Main.main(Main.java:89)
	at java.base@22/java.lang.invoke.LambdaForm$DMH/sa346b79c.invokeStaticInit(LambdaForm$DMH)

public abstract class MyLibrary {
                      ^
<>: error: An exception occurred in com.google.auto.value.processor.AutoValueProcessor:
autovalue.shaded.com.google.escapevelocity.EvaluationException: In expression on line 31 of autovalue.vm: In $pkg.empty: empty does not correspond to a public getter of test, a java.lang.String
	at autovalue.shaded.com.google.escapevelocity.Node.evaluationException(Node.java:55)
	at autovalue.shaded.com.google.escapevelocity.ReferenceNode.evaluationExceptionInThis(ReferenceNode.java:50)
	at autovalue.shaded.com.google.escapevelocity.ReferenceNode$MemberReferenceNode.evaluate(ReferenceNode.java:144)
	at autovalue.shaded.com.google.escapevelocity.ExpressionNode.isTrue(ExpressionNode.java:109)
	at autovalue.shaded.com.google.escapevelocity.ExpressionNode$NotExpressionNode.evaluate(ExpressionNode.java:315)
	at autovalue.shaded.com.google.escapevelocity.ExpressionNode.isTrue(ExpressionNode.java:109)
	at autovalue.shaded.com.google.escapevelocity.DirectiveNode$IfNode.render(DirectiveNode.java:102)
	at autovalue.shaded.com.google.escapevelocity.Node$Cons.render(Node.java:91)
	at autovalue.shaded.com.google.escapevelocity.Template.render(Template.java:189)
	at autovalue.shaded.com.google.escapevelocity.Template.evaluate(Template.java:179)
	at com.google.auto.value.processor.TemplateVars.toText(TemplateVars.java:99)
	at com.google.auto.value.processor.AutoValueProcessor.processType(AutoValueProcessor.java:274)
	at com.google.auto.value.processor.AutoValueishProcessor.process(AutoValueishProcessor.java:441)
	at com.google.turbine.binder.Processing.process(Processing.java:180)
	at com.google.turbine.binder.Binder.bind(Binder.java:120)
	at com.google.turbine.binder.Binder.bind(Binder.java:94)
	at com.google.turbine.main.Main.bind(Main.java:270)
	at com.google.turbine.main.Main.fallback(Main.java:245)
	at com.google.turbine.main.Main.compile(Main.java:172)
	at com.google.turbine.main.Main.compile(Main.java:133)
	at com.google.turbine.main.Main.main(Main.java:89)
	at java.base@22/java.lang.invoke.LambdaForm$DMH/sa346b79c.invokeStaticInit(LambdaForm$DMH)
```

## Appendix: Overriding jars

Here's a way to override the AutoValue jar, for additional testing:

```diff
diff --git a/WORKSPACE.bazel b/WORKSPACE.bazel
index 5d82f9b..85747a0 100644
--- a/WORKSPACE.bazel
+++ b/WORKSPACE.bazel
@@ -1,4 +1,4 @@
-load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
+load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_jar")

 http_archive(
     name = "rules_java",
@@ -32,6 +32,12 @@ rules_jvm_external_setup()

 load("@rules_jvm_external//:defs.bzl", "maven_install")

+http_jar(
+    name = "override_auto_value_jar",
+    url = "https://repo1.maven.org/maven2/com/google/auto/value/auto-value/1.10.3/auto-value-1.10.3.jar",
+    sha256 = "25eb10ca41a1ee5024e68163924518975acee101068f779f4b8b090fca10b606",
+)
+
 maven_install(
     artifacts = [
         "com.google.auto.value:auto-value:1.10.4",
@@ -42,6 +48,9 @@ maven_install(
     repositories = [
         "https://repo1.maven.org/maven2",
     ],
+    override_targets = {
+        "com.google.auto.value:auto-value": "@override_auto_value_jar//jar",
+    },
 )

 load("@maven//:defs.bzl", "pinned_maven_install")
```

The `http_jar` could be replaced with, e.g., a `local_repository` as one wishes.
