package ai.koog.utils.system

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.wcstr
import platform.posix._wputenv_s
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalForeignApi::class)
class SystemReadersTest {
    @Test
    fun testReadUnicodeEnvironmentVariable() = memScoped {
        val name = "KOOG_MINGW_SYSTEM_READERS_TEST_环境".wcstr.ptr
        val value = "配置-🔑"
        assertEquals(0, _wputenv_s(name, value.wcstr.ptr))
        try {
            assertEquals(value, systemConfigReader().getConfigVariable("KOOG_MINGW_SYSTEM_READERS_TEST_环境"))
            assertEquals(value, systemSecretsReader().getSecret("KOOG_MINGW_SYSTEM_READERS_TEST_环境"))
        } finally {
            assertEquals(0, _wputenv_s(name, "".wcstr.ptr))
        }
    }

    @Test
    fun testReadMissingEnvironmentVariable() {
        val name = "KOOG_MINGW_SYSTEM_READERS_MISSING_01727E6B"
        assertNull(systemConfigReader().getConfigVariable(name))
        assertNull(systemSecretsReader().getSecret(name))
    }

    @Test
    fun testRejectNulInEnvironmentVariableName() {
        assertFailsWith<IllegalArgumentException> {
            systemConfigReader().getConfigVariable("PATH\u0000_OTHER")
        }
        assertFailsWith<IllegalArgumentException> {
            systemSecretsReader().getSecret("PATH\u0000_OTHER")
        }
    }
}
