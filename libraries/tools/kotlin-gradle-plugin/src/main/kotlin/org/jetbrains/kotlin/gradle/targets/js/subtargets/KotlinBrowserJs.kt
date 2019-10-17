/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.subtargets

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.BuildVariant
import org.jetbrains.kotlin.gradle.targets.js.dsl.BuildVariantKind
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBrowserDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.npmProject
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Devtool
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode
import org.jetbrains.kotlin.gradle.tasks.KotlinJsDce
import org.jetbrains.kotlin.gradle.tasks.registerTask
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import java.io.File
import javax.inject.Inject

open class KotlinBrowserJs @Inject constructor(target: KotlinJsTarget) :
    KotlinJsSubTarget(target, "browser"),
    KotlinJsBrowserDsl {

    private val dceConfigurations: MutableList<KotlinJsDce.() -> Unit> = mutableListOf()
    private lateinit var dceContainer: KotlinJsDceContainer

    lateinit var buildVariants: NamedDomainObjectContainer<BuildVariant>

    override val testTaskDescription: String
        get() = "Run all ${target.name} tests inside browser using karma and webpack"

    private val webpackTaskName = disambiguateCamelCased("webpack")

    override fun configureDefaultTestFramework(it: KotlinJsTest) {
        it.useKarma {
            useChromeHeadless()
        }
    }

    override fun runTask(body: KotlinWebpack.() -> Unit) {
        (project.tasks.getByName(runTaskName) as KotlinWebpack).body()
    }

    override fun webpackTask(body: KotlinWebpack.() -> Unit) {
        (project.tasks.getByName(webpackTaskName) as KotlinWebpack).body()
    }

    override fun dceTask(body: KotlinJsDce.() -> Unit) {
        dceConfigurations.add(body)
    }

    override fun configureRun(compilation: KotlinJsCompilation) {

        val project = compilation.target.project
        val nodeJs = NodeJsRootPlugin.apply(project.rootProject)

        val dceContainer = if (::dceContainer.isInitialized) {
            this.dceContainer
        } else {
            configureDce(compilation)
        }

        buildVariants.all { buildVariant ->
            val kind = buildVariant.kind
            project.registerTask<KotlinWebpack>(
                disambiguateCamelCased(
                    lowerCamelCaseName(
                        buildVariant.name,
                        "run"
                    )
                )
            ) {
                val compileKotlinTask = compilation.compileKotlinTask
                it.dependsOn(
                    nodeJs.npmInstallTask,
                    target.project.tasks.getByName(compilation.processResourcesTaskName)
                )

                it.configureOptimization(kind)

                it.bin = "webpack-dev-server/bin/webpack-dev-server.js"
                it.compilation = compilation
                it.description = "start ${kind.name.toLowerCase()} webpack dev server"

                it.devServer = KotlinWebpackConfig.DevServer(
                    open = true,
                    contentBase = listOf(compilation.output.resourcesDir.canonicalPath)
                )

                it.outputs.upToDateWhen { false }

                when (kind) {
                    BuildVariantKind.RELEASE -> {
                        it.entry = dceContainer.destinationDirProvider().resolve(compilation.compileKotlinTask.outputFile.name)
                        it.resolveFromModulesFirst = true
                        it.dependsOn(dceContainer.taskProvider)
                    }
                    BuildVariantKind.DEBUG -> {
                        it.dependsOn(compileKotlinTask)
                        target.runTask.dependsOn(it)
                    }
                }
            }
        }
    }

    override fun configureBuild(compilation: KotlinJsCompilation) {
        val project = compilation.target.project
        val nodeJs = NodeJsRootPlugin.apply(project.rootProject)

        val dceContainer = if (::dceContainer.isInitialized) {
            this.dceContainer
        } else {
            configureDce(compilation)
        }

        buildVariants.all { buildVariant ->
            val kind = buildVariant.kind
            project.registerTask<KotlinWebpack>(
                disambiguateCamelCased(
                    lowerCamelCaseName(
                        buildVariant.name,
                        "webpack"
                    )
                )
            ) {
                val compileKotlinTask = compilation.compileKotlinTask
                it.dependsOn(
                    nodeJs.npmInstallTask
                )

                it.configureOptimization(kind)

                it.compilation = compilation
                it.description = "build webpack ${kind.name.toLowerCase()} bundle"

                when (kind) {
                    BuildVariantKind.RELEASE -> {
                        it.entry = dceContainer.destinationDirProvider().resolve(compilation.compileKotlinTask.outputFile.name)
                        it.resolveFromModulesFirst = true
                        it.dependsOn(dceContainer.taskProvider)
                        project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(it)
                    }
                    BuildVariantKind.DEBUG -> {
                        it.dependsOn(compileKotlinTask)
                    }
                }
            }
        }
    }

    private fun configureDce(compilation: KotlinJsCompilation): KotlinJsDceContainer {
        val project = compilation.target.project

        val dceTaskName = lowerCamelCaseName(
            DCE_TASK_PREFIX,
            compilation.target.disambiguationClassifier,
            compilation.name.takeIf { it != KotlinCompilation.MAIN_COMPILATION_NAME },
            DCE_TASK_SUFFIX
        )

        val kotlinTask = compilation.compileKotlinTask

        val destinationDir = compilation.npmProject.dir
            .resolve(DCE_DIR)

        var destinationDirProvider: () -> File = { destinationDir }

        return project.registerTask<KotlinJsDce>(dceTaskName) {
            dceConfigurations.forEach { configuration ->
                it.configuration()
            }

            it.dependsOn(kotlinTask)

            it.classpath = project.configurations.getByName(compilation.compileDependencyConfigurationName)

            destinationDirProvider = {
                it.dceOptions.outputDirectory?.let { File(it) }
                    ?: destinationDir
            }

            it.destinationDir = destinationDirProvider()
            it.source(kotlinTask.outputFile)
        }.let {
            KotlinJsDceContainer(
                taskProvider = it,
                destinationDirProvider = destinationDirProvider
            ).also {
                dceContainer = it
            }
        }
    }

    private fun KotlinWebpack.configureOptimization(kind: BuildVariantKind) {
        mode = getByKind(
            kind = kind,
            releaseValue = Mode.PRODUCTION,
            debugValue = Mode.DEVELOPMENT
        )

        devtool = getByKind(
            kind = kind,
            releaseValue = Devtool.SOURCE_MAP,
            debugValue = Devtool.EVAL_SOURCE_MAP
        )
    }

    private fun <T> getByKind(
        kind: BuildVariantKind,
        releaseValue: T,
        debugValue: T
    ): T = when (kind) {
        BuildVariantKind.RELEASE -> releaseValue
        BuildVariantKind.DEBUG -> debugValue
    }

    override fun configureBuildVariants() {
        buildVariants = project.container(BuildVariant::class.java)
        buildVariants.create(RELEASE) {
            it.kind = BuildVariantKind.RELEASE
        }
        buildVariants.create(DEBUG) {
            it.kind = BuildVariantKind.DEBUG
        }
    }

    private data class KotlinJsDceContainer(
        val taskProvider: TaskProvider<KotlinJsDce>,
        // Store DCE's destination dir provider instead of destination dir explicitly
        // Useful because due to Task Configuration Avoidance we don't know final destinationDir of KotlinJsDce
        val destinationDirProvider: () -> File
    )

    companion object {
        const val DCE_TASK_PREFIX = "processDce"
        const val DCE_TASK_SUFFIX = "kotlinJs"

        const val DCE_DIR = "kotlin-dce"

        const val RELEASE = "release"
        const val DEBUG = "debug"
    }
}