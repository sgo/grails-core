package org.codehaus.groovy.grails.resolve

import grails.util.*
import static groovy.lang.GroovySystem.*
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.id.ModuleId

class PluginInstallEngineTests extends GroovyTestCase {

    void test_persist_metadata_when_already_registered_in_metadata_with_different_version() {
        def registered = ['test-plugin':'0.1']
        def dependencies = []
        def plugin = 'test-plugin'
        def pluginVersion = '2.3'
        def includedInMetadata = true

        doTest(registered, dependencies, plugin, pluginVersion, includedInMetadata)
    }

    void test_persist_metadata_when_plugin_not_contained_in_neither_metadata_nor_plugin_dependencies() {
        def registered = [:]
        def dependencies = []
        def plugin = 'test-plugin'
        def pluginVersion = '0.1'
        def includedInMetadata = true

        doTest(registered, dependencies, plugin, pluginVersion, includedInMetadata)
    }

    void test_do_not_persist_metadata_when_transitive_plugin_dependencies_are_exported() {
        def registered = [:]
        def dependencies = [transitiveExported('test-plugin')]
        def plugin = 'test-plugin'
        def pluginVersion = '0.1'
        def includedInMetadata = false

        doTest(registered, dependencies, plugin, pluginVersion, includedInMetadata)
    }

    void test_persist_metadata_when_transitive_plugin_dependencies_are_not_exported() {
        def registered = [:]
        def dependencies = [transitiveNotExported('test-plugin')]
        def plugin = 'test-plugin'
        def pluginVersion = '0.1'
        def includedInMetadata = true

        doTest(registered, dependencies, plugin, pluginVersion, includedInMetadata)
    }

    void test_do_not_persist_metadata_when_non_transitive_plugin_dependency_exported() {
        def registered = [:]
        def dependencies = [exported('test-plugin')]
        def plugin = 'test-plugin'
        def pluginVersion = '0.1'
        def includedInMetadata = false

        doTest(registered, dependencies, plugin, pluginVersion, includedInMetadata)
    }

    void test_do_not_persist_metadata_when_non_transitive_plugin_dependency_not_exported() {
        def registered = [:]
        def dependencies = [notExported('test-plugin')]
        def plugin = 'test-plugin'
        def pluginVersion = '0.1'
        def includedInMetadata = false

        doTest(registered, dependencies, plugin, pluginVersion, includedInMetadata)
    }

    def exported(plugin) {
        descriptor(plugin: plugin, export: true)
    }

    private notExported(plugin) {
        descriptor(plugin: plugin, export: false)
    }

    def transitiveExported(plugin) {
        descriptor(upstream: 'upstream-plugin', plugin: plugin, export: true)
    }

    private transitiveNotExported(plugin) {
        descriptor(upstream: 'upstream-plugin', plugin: plugin, export: false)
    }

    private EnhancedDefaultDependencyDescriptor descriptor(map) {
        def descriptor = new EnhancedDefaultDependencyDescriptor(new ModuleRevisionId(new ModuleId('org.test', map.plugin), '0.1'), true, 'compile')
        descriptor.plugin = map.upstream
        descriptor.export = map.export
        return descriptor
    }

    private def doTest(registered, dependencies, String plugin, String pluginVersion, boolean includedInMetadata) {
        def metadata = new MetadataStorage()
        metadata.putAll(registered)
        def dependencyManager = dependencyManager(registered, dependencies)
        def engine = systemUnderTest(dependencyManager, metadataPersistingToStorage(metadata))

        engine.registerPluginWithMetadata(plugin, pluginVersion)

        assert metadata.contains(plugin, pluginVersion) == includedInMetadata
    }

    private class MetadataStorage {

        private def metadata = [:]

        void putAll(Map map) {
            metadata.putAll(map)
        }

        boolean contains(plugin, version) {
            def hasKey = metadata["plugins.$plugin"]
            def hasVersion = metadata["plugins.$plugin"] == version
            return hasKey && hasVersion
        }
    }

    private PluginInstallEngine systemUnderTest(IvyDependencyManager dependencyManager, Metadata metadata) {
        def buildSettings = new BuildSettings()
        def pluginBuildSettings = new PluginBuildSettings(buildSettings)
        def engine = new PluginInstallEngine(buildSettings, pluginBuildSettings, metadata)
        // setting dependency manager after engine instantiation to avoid constructor hell
        buildSettings.setDependencyManager(dependencyManager)
        return engine
    }

    private Metadata metadataPersistingToStorage(storage) {
        Metadata.metaClass.persist = {-> storage.putAll(delegate)}
        return new Metadata()
    }

    private IvyDependencyManager dependencyManager(registered, dependencies) {
        IvyDependencyManager.metaClass.getMetadataRegisteredPluginNames = {-> registered.keySet()}
        IvyDependencyManager.metaClass.getPluginDependencyDescriptors = {-> dependencies}
        def dependencyManager = new IvyDependencyManager('test', '0.1')
        return dependencyManager
    }

    @Override
    protected void tearDown() {
        metaClassRegistry.removeMetaClass IvyDependencyManager
        metaClassRegistry.removeMetaClass Metadata
    }
}
