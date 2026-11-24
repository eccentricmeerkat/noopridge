package com.noop.ai

import android.content.Context
import com.noop.data.SecurePrefs
import com.noop.data.WhoopDao
import com.noop.data.WhoopRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * R10c (QA matrix, Sage row): how Sage handles each provider answer, against a MockWebServer on this machine through the
 * Custom provider, so no request leaves it. 200, 401, 403, 429, 500, a timeout, no connection, consent off and a
 * rotated key; and, for every error, the key never reaches the message shown on screen (I-05).
 *
 * The repository behind the coach throws on any read, so with consent off the test also proves no data was read.
 */
@RunWith(RobolectricTestRunner::class)
class SageProviderResponsesTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    private val keyA = "sk-test-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val keyB = "sk-test-BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"

    @Before
    fun setUp() {
        // No Android Keystore under Robolectric: a plain file stands in for the encrypted one.
        SecurePrefs.useForTest("noop_ai_secure_prefs", context.getSharedPreferences("sage_test_secure", Context.MODE_PRIVATE))
        AiKeyStore.clear(context)
        AiKeyStore.save(context, keyA, owner = AiProvider.CUSTOM)
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val throwingRepo: WhoopRepository
        get() = WhoopRepository(
            Proxy.newProxyInstance(WhoopDao::class.java.classLoader, arrayOf(WhoopDao::class.java)) { _, m, _ ->
                throw UnsupportedOperationException("Sage read the database (${m.name})")
            } as WhoopDao,
        )

    private fun coach(client: OkHttpClient = AiCoach.defaultHttpClient()) = AiCoach(throwingRepo, http = client)

    private fun baseUrl() = server.url("/v1").toString()

    private fun ask(coach: AiCoach = coach(), consent: Boolean = false, base: String = baseUrl()): String = runBlocking {
        coach.chat(
            context, listOf(ChatMsg(role = "user", text = "How did I sleep?")), AiProvider.CUSTOM, "test-model",
            consent = consent, customBaseUrl = base,
        )
    }

    private fun reply(text: String) = MockResponse().setResponseCode(200).setBody(
        JSONObject().put("choices", org.json.JSONArray().put(
            JSONObject().put("message", JSONObject().put("role", "assistant").put("content", text)).put("finish_reason", "stop"),
        )).toString(),
    )

    /** An error body that echoes the key, as OpenAI's 401 does with a masked one and some gateways do whole. */
    private fun error(code: Int) = MockResponse().setResponseCode(code)
        .setBody(JSONObject().put("error", JSONObject().put("message", "Bad key: $keyA")).toString())

    private fun failure(block: () -> Unit): Exception {
        try {
            block()
        } catch (e: Exception) {
            return e
        }
        fail("expected a failure")
        throw IllegalStateException()
    }

    private fun assertNoKey(message: String?) {
        val text = message.orEmpty()
        assertFalse("the key reached the message: $text", keyA in text || keyB in text)
        assertFalse("a key-shaped token reached the message: $text", Regex("""sk-[A-Za-z0-9_-]{8,}""").containsMatchIn(text))
    }

    @Test
    fun aReplyIsReturned() {
        server.enqueue(reply("You slept well."))
        assertEquals("You slept well.", ask())
        val sent = server.takeRequest()
        assertEquals("/v1/chat/completions", sent.path)
        assertEquals("Bearer $keyA", sent.getHeader("Authorization"))
    }

    @Test
    fun aRejectedKeyIsNamedAsSuch() {
        for (code in listOf(401, 403)) {
            server.enqueue(error(code))
            val e = failure { ask() }
            assertTrue("$code is a key rejection", e is AiKeyRejectedException)
            assertTrue(e.message!!.contains("API key was rejected"))
            assertNoKey(e.message)
        }
    }

    @Test
    fun rateLimitAndServerErrorsSaySo() {
        server.enqueue(error(429))
        val limited = failure { ask() }
        assertFalse(limited is AiKeyRejectedException)
        assertTrue(limited.message!!.contains("rate limit"))
        assertNoKey(limited.message)

        server.enqueue(error(500))
        val broken = failure { ask() }
        assertFalse(broken is AiKeyRejectedException)
        assertTrue(broken.message!!.contains("server error (HTTP 500)"))
        assertNoKey(broken.message)
    }

    @Test
    fun aStreamedRejectionKeepsTheKeyOut() {
        server.enqueue(error(401))
        val e = failure {
            runBlocking {
                coach().chatStream(
                    context, listOf(ChatMsg(role = "user", text = "Hi")), AiProvider.CUSTOM, "test-model",
                    customBaseUrl = baseUrl(),
                ) { }
            }
        }
        assertTrue(e is AiKeyRejectedException)
        assertNoKey(e.message)
    }

    @Test
    fun aTimeoutSaysSo() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val quick = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build()
        val e = failure { ask(coach(quick)) }
        assertTrue(e.message, e.message!!.contains("timed out"))
    }

    @Test
    fun noConnectionSaysSo() {
        val offline = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException("offline")
        }).build()
        val e = failure { ask(coach(offline), base = "http://localhost:${server.port}/v1") }
        assertTrue(e.message, e.message!!.contains("No internet connection"))
        assertEquals("nothing reached the server", 0, server.requestCount)
    }

    @Test
    fun consentOffSendsTheNoteAndNoNumbers() {
        server.enqueue(reply("General advice."))
        ask(consent = false)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        val messages = body.getJSONArray("messages")
        val question = messages.getJSONObject(messages.length() - 1).getString("content")
        assertTrue(question.startsWith(AiCoach.NO_CONSENT_NOTE))
        assertTrue(question.endsWith("My question: How did I sleep?"))
        assertFalse("no numbers beyond the question", Regex("""\d""").containsMatchIn(question))
    }

    /** One streamed SSE reply in the OpenAI shape. */
    private fun streamed(text: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "text/event-stream")
        .setBody(
            "data: " + JSONObject().put("choices", org.json.JSONArray().put(
                JSONObject().put("delta", JSONObject().put("content", text)),
            )).toString() + "\n\ndata: [DONE]\n\n",
        )

    @Test
    fun aStreamRejectingTheClassicParamsRetriesWithTheModernOnes() {
        // GPT-5 and the reasoning models reject max_tokens / temperature; the stream Sage uses used to stop there.
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                JSONObject().put("error", JSONObject().put("message", "Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead.")).toString(),
            ),
        )
        server.enqueue(streamed("Hello there"))
        val got = StringBuilder()
        runBlocking {
            coach().chatStream(
                context, listOf(ChatMsg(role = "user", text = "Hi")), AiProvider.CUSTOM, "gpt-5-mini", customBaseUrl = baseUrl(),
            ) { got.append(it) }
        }
        assertEquals("Hello there", got.toString())
        val first = JSONObject(server.takeRequest().body.readUtf8())
        val second = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(first.has("max_tokens") && first.has("temperature"))
        assertTrue(second.has("max_completion_tokens"))
        assertFalse(second.has("max_tokens") || second.has("temperature"))
    }

    @Test
    fun anUnrelated400IsNotRetried() {
        server.enqueue(MockResponse().setResponseCode(400).setBody(JSONObject().put("error", JSONObject().put("message", "Bad request body")).toString()))
        val e = failure {
            runBlocking {
                coach().chatStream(
                    context, listOf(ChatMsg(role = "user", text = "Hi")), AiProvider.CUSTOM, "m", customBaseUrl = baseUrl(),
                ) { }
            }
        }
        assertTrue(e.message!!.contains("HTTP 400"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aBusyProviderSaysSo() {
        server.enqueue(MockResponse().setResponseCode(503).setBody(JSONObject().put("error", JSONObject().put("message", "This model is currently experiencing high demand.")).toString()))
        val e = failure { ask() }
        assertTrue(e.message, e.message!!.contains("is busy right now"))
    }

    @Test
    fun aSavedSlowGeminiDefaultMovesToTheFastOne() {
        assertEquals("gemini-3.5-flash-lite", AiProvider.GEMINI.defaultModel)
        assertEquals("gemini-3.5-flash-lite", AiKeyStore.resolvedModel(AiProvider.GEMINI, "gemini-flash-latest"))
        assertEquals("gemini-3.5-flash-lite", AiKeyStore.resolvedModel(AiProvider.GEMINI, null))
        // A model the wearer picked is kept.
        assertEquals("gemini-3.6-flash", AiKeyStore.resolvedModel(AiProvider.GEMINI, "gemini-3.6-flash"))
        assertEquals("gpt-5-mini", AiKeyStore.resolvedModel(AiProvider.OPENAI, null))
    }

    @Test
    fun theTilesSayWhichKeysNeedPaidCredits() {
        assertEquals(true, AiProvider.GEMINI.freeTier)
        assertEquals(false, AiProvider.OPENAI.freeTier)
        assertEquals(false, AiProvider.ANTHROPIC.freeTier)
        assertNull(AiProvider.CUSTOM.freeTier)
    }

    @Test
    fun aRotatedKeyIsTheOneSent() {
        server.enqueue(reply("one"))
        ask()
        assertEquals("Bearer $keyA", server.takeRequest().getHeader("Authorization"))

        AiKeyStore.save(context, keyB, owner = AiProvider.CUSTOM)
        server.enqueue(reply("two"))
        ask()
        assertEquals("Bearer $keyB", server.takeRequest().getHeader("Authorization"))

        // A key saved for a cloud provider is never sent to a Custom server.
        AiKeyStore.save(context, keyA, owner = AiProvider.OPENAI)
        server.enqueue(reply("three"))
        ask()
        assertNull(server.takeRequest().getHeader("Authorization"))

        // Disconnect deletes it.
        AiKeyStore.clear(context)
        assertNull(AiKeyStore.read(context))
    }
}
