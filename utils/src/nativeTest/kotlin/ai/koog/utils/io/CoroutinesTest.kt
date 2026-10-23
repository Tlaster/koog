package ai.koog.utils.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.test.Test
import kotlin.test.assertSame

class CoroutinesTest {
    @Test
    fun testSuitableForIOUsesIODispatcher() {
        assertSame(Dispatchers.IO, Dispatchers.SuitableForIO)
    }
}
