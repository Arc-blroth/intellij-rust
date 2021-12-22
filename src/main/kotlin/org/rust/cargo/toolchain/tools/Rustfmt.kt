/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.RustChannel
import org.rust.ide.actions.RustfmtEditSettingsAction
import org.rust.ide.notifications.showBalloon
import org.rust.lang.core.psi.ext.edition
import org.rust.lang.core.psi.isNotRustFile
import org.rust.openapiext.*
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.unwrapOrElse
import java.nio.file.Path

fun RsToolchainBase.rustfmt(): Rustfmt = Rustfmt(this)

class Rustfmt(toolchain: RsToolchainBase) : RustupComponent(NAME, toolchain) {

    fun reformatDocumentTextOrNull(cargoProject: CargoProject, document: Document): String? {
        val project = cargoProject.project
        return createCommandLine(cargoProject, document)
            ?.execute(project, stdIn = document.text.toByteArray())
            ?.unwrapOrElse { e ->
                e.showRustfmtError(project)
                if (isUnitTestMode) throw e else return null
            }?.stdout
    }

    fun createCommandLine(cargoProject: CargoProject, document: Document): GeneralCommandLine? {
        val file = document.virtualFile ?: return null
        if (file.isNotRustFile || !file.isValid) return null

        val project = cargoProject.project
        val settings = project.rustfmtSettings

        val arguments = ParametersListUtil.parse(settings.additionalArguments).toMutableList()

        val toolchain = arguments.firstOrNull()?.takeIf { it.startsWith("+") }
        if (toolchain == null && settings.channel != RustChannel.DEFAULT) {
            arguments.add(0, "+${settings.channel}")
        }

        val idx = arguments.indexOf("--emit")
        if (idx in 0 until arguments.size - 1) arguments.removeAt(idx + 1)
        arguments.removeAll { it.startsWith("--emit") }
        arguments.add("--emit=stdout")

        arguments.addArgument("config-path") {
            findConfigPathRecursively(file.parent, stopAt = cargoProject.workingDirectory)?.toString()
        }

        arguments.addArgument("edition") {
            if (cargoProject.rustcInfo?.version == null) return@addArgument null
            val edition = runReadAction {
                val psiFile = file.toPsiFile(project)
                psiFile?.edition ?: Edition.DEFAULT
            }
            edition.presentation
        }

        return createBaseCommandLine(arguments, cargoProject.workingDirectory, settings.envs)
    }

    fun reformatCargoProject(
        cargoProject: CargoProject,
        owner: Disposable = cargoProject.project
    ): RsProcessResult<Unit> {
        val project = cargoProject.project
        val settings = project.rustfmtSettings
        val arguments = ParametersListUtil.parse(settings.additionalArguments).toMutableList()
        val toolchain = if (arguments.firstOrNull()?.startsWith("+") == true) {
            arguments.removeFirst()
        } else {
            null
        }
        val commandLine = CargoCommandLine.forProject(
            cargoProject,
            "fmt",
            listOf("--all", "--") + arguments,
            toolchain,
            settings.channel,
            EnvironmentVariablesData.create(settings.envs, true)
        )

        return project.computeWithCancelableProgress("Reformatting Cargo Project with Rustfmt...") {
            project.toolchain
                ?.cargoOrWrapper(cargoProject.workingDirectory)
                ?.toGeneralCommandLine(project, commandLine)
                ?.execute(owner)
                ?.map { }
                ?.mapErr { e ->
                    e.showRustfmtError(project)
                    e
                }
                ?: Ok(Unit)
        }
    }

    companion object {
        const val NAME: String = "rustfmt"

        private val CONFIG_FILES: List<String> = listOf("rustfmt.toml", ".rustfmt.toml")

        private fun MutableList<String>.addArgument(flagName: String, value: () -> String?) {
            if (any { it.startsWith("--$flagName") }) return
            val flagValue = value() ?: return
            add("--$flagName=$flagValue")
        }

        private fun findConfigPathRecursively(directory: VirtualFile, stopAt: Path): Path? {
            val path = directory.pathAsPath
            if (!path.startsWith(stopAt) || path == stopAt) return null
            if (directory.children.any { it.name in CONFIG_FILES }) return path
            return findConfigPathRecursively(directory.parent, stopAt)
        }

        private fun RsProcessExecutionException.showRustfmtError(project: Project) {
            val message = message.orEmpty().trimEnd('\n')
            if (message.isNotEmpty()) {
                project.showBalloon("Rustfmt", message, NotificationType.ERROR, RustfmtEditSettingsAction("Show settings..."))
            }
        }
    }
}
