package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OpenAIResponsesReplayTest {
    @Test
    fun testReplayPreservesOutputMessageGroupsAndFunctionItemIdentity() = runTest {
        val input = mutableListOf<String>()
        val engine = MockEngine { request ->
            input += (request.body as TextContent).text
            respond(response(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        HttpClient(engine).use { http ->
            val client = OpenAILLMClient(apiKey = "test-key", httpClientFactory = KtorKoogHttpClient.Factory(baseClient = http))
            val model = OpenAIModels.Chat.GPT4o
            val initial = Prompt.build("test", params = OpenAIResponsesParams(store = false)) { user("Start") }
            val response = client.execute(initial, model)
            assertEquals("tool_calls", response.finishReason)
            val restored = Json.decodeFromString<Message>(Json.encodeToString<Message>(response)) as Message.Assistant
            val replay = initial.withMessages { it + restored }
            client.execute(replay, model)
            val items = Json.parseToJsonElement(input.last()).jsonObject.getValue("input").jsonArray.drop(1).map { it.jsonObject }
            assertEquals(listOf("msg_progress", "fc_1", "msg_final"), items.map { it.getValue("id").jsonPrimitive.content })
            assertEquals("commentary", items[0].getValue("phase").jsonPrimitive.content)
            assertEquals(2, items[0].getValue("content").jsonArray.size)
            assertEquals("call_1", items[1].getValue("call_id").jsonPrimitive.content)
            assertEquals("final_answer", items[2].getValue("phase").jsonPrimitive.content)

            val edited = restored.copy(parts = listOf(MessagePart.Text("Shorter")) + restored.parts.filterIsInstance<MessagePart.Tool.Call>())
            client.execute(initial.withMessages { it + edited }, model)
            val rewritten = Json.parseToJsonElement(input.last()).jsonObject.getValue("input").jsonArray.drop(1)
            assertEquals(2, rewritten.size)
            assertNull(rewritten.first().jsonObject["phase"])
            assertEquals(
                "Shorter",
                rewritten.first().jsonObject.getValue("content").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content
            )
        }
    }

    @Test
    fun testUnfinishedResponsesNeverReturnToolCalls() = runTest {
        for ((status, itemStatus, expected) in listOf(
            Triple("incomplete", "completed", "max_output_tokens"),
            Triple("failed", "completed", "server error"),
            Triple("in_progress", "completed", "in_progress"),
            Triple("cancelled", "completed", "cancelled"),
            Triple("completed", "incomplete", "incomplete")
        )) {
            val engine = MockEngine {
                respond(response(status, itemStatus), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            HttpClient(engine).use { http ->
                val client = OpenAILLMClient(apiKey = "test-key", httpClientFactory = KtorKoogHttpClient.Factory(baseClient = http))
                val prompt = Prompt.build("test", params = OpenAIResponsesParams(store = false)) { user("Start") }
                val error = assertFailsWith<LLMClientException> { client.execute(prompt, OpenAIModels.Chat.GPT4o) }
                assertContains(error.message.orEmpty(), expected)
            }
        }
    }

    private fun response(status: String = "completed", itemStatus: String = "completed"): String {
        val detail = when (status) {
            "incomplete" -> """"incomplete_details":{"reason":"max_output_tokens"},"""
            "failed" -> """"error":{"code":"server_error","message":"server error"},"""
            else -> ""
        }
        return """
            {"id":"resp_test","object":"response","created_at":0,"model":"test","status":"$status",
            "parallel_tool_calls":true,"text":{"format":{"type":"text"}},$detail"output":[
              {"type":"message","id":"msg_progress","role":"assistant","phase":"commentary","status":"completed",
               "content":[{"type":"output_text","text":"Checking","annotations":[]},
                          {"type":"output_text","text":" now.","annotations":[]}]},
              {"type":"function_call","id":"fc_1","call_id":"call_1","name":"echo","arguments":"{}","status":"$itemStatus"},
              {"type":"message","id":"msg_final","role":"assistant","phase":"final_answer","status":"completed",
               "content":[{"type":"output_text","text":"Done","annotations":[]}]}
            ]}
        """.trimIndent()
    }
}
