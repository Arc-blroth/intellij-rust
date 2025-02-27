/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig.legacy

import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.project.Project
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.BuildResult
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.debugger.isDebuggingIntegrationEnabled
import org.rust.debugger.runconfig.RsDebugRunnerUtils

class RsDebugRunnerLegacy : RsDebugRunnerLegacyBase() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        super.canRun(executorId, profile) &&
            profile is CargoCommandConfiguration &&
            profile.project.toolchain !is RsWslToolchain &&
            isDebuggingIntegrationEnabled()

    override fun checkToolchainSupported(project: Project, host: String): BuildResult.ToolchainError? =
        RsDebugRunnerUtils.checkToolchainSupported(project, host)

    override fun checkToolchainConfigured(project: Project): Boolean =
        RsDebugRunnerUtils.checkToolchainConfigured(project)
}
