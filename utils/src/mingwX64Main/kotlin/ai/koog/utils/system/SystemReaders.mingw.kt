package ai.koog.utils.system

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.wcstr
import platform.posix._wgetenv

/** Reads Windows configuration from environment variables. */
public actual fun systemConfigReader(): SystemConfigReader = WindowsEnvironmentReader

/** Reads Windows secrets from environment variables. */
public actual fun systemSecretsReader(): SystemSecretsReader = WindowsEnvironmentReader

@OptIn(ExperimentalForeignApi::class)
private object WindowsEnvironmentReader : SystemConfigReader, SystemSecretsReader {
    override fun getConfigVariable(name: String): String? = readVariable(name)

    override fun getSecret(name: String): String? = readVariable(name)

    private fun readVariable(name: String): String? {
        require('\u0000' !in name) { "Environment variable names must not contain NUL characters" }
        return memScoped {
            _wgetenv(name.wcstr.ptr)?.toKStringFromUtf16()
        }
    }
}
