from pathlib import Path
import hashlib

def edit(name, expected, transform):
    path = Path(name)
    data = path.read_bytes()
    assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == expected, name
    path.write_text(transform(data.decode()))

def lifecycle(text):
    text = text.replace('import com.balugaq.jeg.utils.GuideUtil;', 'import com.balugaq.jeg.utils.GuideUtil;\nimport com.balugaq.jeg.utils.GuideRuntimeClasses;')
    needle = '    public JustEnoughGuide() {'
    assert text.count(needle) == 1
    text = text.replace(needle, '    private boolean rejectedIncompleteRuntime;\n\n' + needle)
    needle = '    public void onEnable() {\n        instance = this;'
    replacement = '''    public void onEnable() {
        rejectedIncompleteRuntime = false;
        try {
            int linked = GuideRuntimeClasses.preload(getClassLoader());
            getLogger().info("Verified " + linked + " guide renderer classes before guide registration.");
        } catch (ClassNotFoundException | LinkageError | SecurityException failure) {
            rejectedIncompleteRuntime = true;
            getLogger().log(Level.SEVERE,
                "JEG guide renderer classes could not be loaded from " + getFile().getName()
                    + ". Stop the server, replace JEG with the complete release JAR, remove duplicate JEG JARs,"
                    + " and restart. If necessary, regenerate only Paper's cached JEG remapped JAR while stopped."
                    + " Do not delete bookmarks, player data or Slimefun storage. The existing guide is not replaced.", failure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        instance = this;'''
    assert text.count(needle) == 1
    text = text.replace(needle, replacement)
    needle = '    public void onDisable() {\n        unloadInternal();'
    replacement = '''    public void onDisable() {
        if (rejectedIncompleteRuntime) {
            // Verification failed before any managers, items or guides were installed.
            instance = null;
            return;
        }
        unloadInternal();'''
    assert text.count(needle) == 1
    return text.replace(needle, replacement)

def gradle(text):
    text=text.replace('version = "2.1.69"', 'version = "2.1.70"')
    text=text.replace('// 2.1.69: Paper 26.3 for-removal API compatibility while retaining the 1.21.11 floor.', '// 2.1.70: verify and preload guide renderer classes without changing guide data or behavior.')
    needle='        mergeServiceFiles()'
    addition='''

        doLast {
            val compiled = sourceSets.main.get().output.classesDirs.files.flatMap { root ->
                root.resolve("com/balugaq/jeg").walkTopDown().filter { it.isFile && it.extension == "class" }
                    .map { it.relativeTo(root).invariantSeparatorsPath }.toList()
            }.toSet()
            check(compiled.isNotEmpty()) { "No compiled JEG classes to verify" }
            java.util.zip.ZipFile(archiveFile.get().asFile).use { jar ->
                val missing = compiled.filter { jar.getEntry(it) == null }
                check(missing.isEmpty()) { "Missing JEG runtime classes: $missing" }
                check(jar.getEntry("com/balugaq/jeg/utils/clickhandler/OnDisplay\\$ItemGroup.class") != null) {
                    "Missing guide item-group renderer"
                }
            }
        }'''
    assert text.count(needle)==1
    return text.replace(needle,needle+addition)

def workflow(text):
    text=text.replace('actions/checkout@v4','actions/checkout@v6').replace('actions/setup-java@v4','actions/setup-java@v5').replace('actions/upload-artifact@v4','actions/upload-artifact@v7')
    needle='      - name: Build\n'
    addition='''      - name: Guide renderer regression tests
        run: |
          python3 scripts/test_guide_runtime_jar.py
          mkdir -p build/renderer-test
          javac --release 21 -d build/renderer-test src/main/java/com/balugaq/jeg/utils/GuideRuntimeClasses.java tests/GuideRuntimeClassesTest.java
          java -cp build/renderer-test com.balugaq.jeg.utils.GuideRuntimeClassesTest build/renderer-test

'''
    assert text.count(needle)==1
    text=text.replace(needle,addition+needle)
    text=text.replace('./gradlew clean shadowJar --no-daemon','./gradlew clean build --no-daemon')
    needle='      - name: Upload addon JAR\n'
    addition='''      - name: Verify complete guide runtime artifact
        run: python3 scripts/verify_guide_runtime_jar.py "build/libs/SF_JustEnoughGuide${{ steps.version.outputs.version }}.jar" --classes build/classes/java/main --version "${{ steps.version.outputs.version }}"

'''
    assert text.count(needle)==1
    text=text.replace(needle,addition+needle)
    needle='          path: build/libs/SF_JustEnoughGuide${{ steps.version.outputs.version }}.jar\n'
    assert text.count(needle)==1
    return text.replace(needle,needle+'          archive: false\n')

def release(text):
    text=text.replace('actions/checkout@v4','actions/checkout@v6').replace('actions/setup-java@v4','actions/setup-java@v5').replace('actions/upload-artifact@v4','actions/upload-artifact@v7')
    text=text.replace('./gradlew clean shadowJar --no-daemon','./gradlew clean build --no-daemon')
    needle='          test -s "$JAR"\n'
    assert text.count(needle)==1
    text=text.replace(needle,needle+'          python3 scripts/test_guide_runtime_jar.py\n          python3 scripts/verify_guide_runtime_jar.py "$JAR" --classes build/classes/java/main --version "${{ steps.version.outputs.version }}"\n')
    needle='          path: build/libs/SF_JustEnoughGuide${{ steps.version.outputs.version }}.jar\n'
    assert text.count(needle)==1
    return text.replace(needle,needle+'          archive: false\n')

edit('src/main/java/com/balugaq/jeg/implementation/JustEnoughGuide.java','5e99d6439db413983a88c90d8de99ea36be17a32',lifecycle)
edit('build.gradle.kts','29caa28c64a601e858ac6130a7e06cd52ddbfe31',gradle)
edit('.github/workflows/build.yml','498a9cfe1e525596618d5d38fcc109aff91ee98a',workflow)
edit('.github/workflows/release.yml','cde21e0ac9cacb747765ccb5f7dcb93b7c4c2f2e',release)
