package com.noop.ai

/**
 * AI Coach providers. The user brings their own API key for one of these and the app
 * sends a compact text summary of their metrics + their question to the chosen provider.
 *
 * Provider names are the only branding shown — there is no app-author / model-vendor
 * branding beyond the provider name the user picks.
 *
 * Wire formats are encoded in [AiCoach]; this enum only carries the display name, the
 * default model, the curated list of selectable models, the chat HTTPS endpoint, and the
 * models-list endpoint used to fetch the provider's live catalogue.
 *
 * The [models] list is a sensible, curated starting point — the user can also fetch the
 * provider's live list (merged in at runtime) or type any model id via "Custom…", so the
 * app never has to ship an exhaustive or up-to-date catalogue.
 */
enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val models: List<String>,
    val endpoint: String,
    val modelsEndpoint: String,
    /** Sage's setup tiles (owner, 15 Nov 2026): true when the provider's API has a free tier (Gemini), false when a key
     *  needs paid credits (OpenAI, Anthropic), null when it depends on the server (Custom). */
    val freeTier: Boolean? = null,
) {
    OPENAI(
        displayName = "OpenAI",
        defaultModel = "gpt-5-mini",
        // Pinned ids, not aliases: OpenAI has no stable per-tier "-latest" alias the way Gemini does
        // (#400), so this list is bumped by hand. The live /models refresh stays the authority for
        // anything released after this.
        //
        // The reasoning tiers (o3, o4-mini) and the GPT-5 family reject `temperature` and
        // `max_tokens`. Nothing special is needed for them here: [AiCoach] sends the classic
        // parameters and, on a 400 naming one of them, retries with `max_completion_tokens` and no
        // temperature. One extra round trip on the first message, not a per-model table to maintain.
        // Twin of the Swift `AIProvider.modelOptions`.
        models = listOf(
            "gpt-5",
            "gpt-5-mini",
            "gpt-5-nano",
            "gpt-4.1",
            "gpt-4.1-mini",
            "gpt-4.1-nano",
            "gpt-4o",
            "gpt-4o-mini",
            "o3",
            "o4-mini",
        ),
        endpoint = "https://api.openai.com/v1/chat/completions",
        modelsEndpoint = "https://api.openai.com/v1/models",
        freeTier = false,
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        defaultModel = "claude-sonnet-4-6",
        models = listOf(
            "claude-opus-4-8",
            "claude-sonnet-4-6",
            "claude-haiku-4-5-20251001",
            "claude-3-7-sonnet-latest",
            "claude-3-5-sonnet-latest",
            "claude-3-5-haiku-latest",
            "claude-3-opus-latest",
        ),
        endpoint = "https://api.anthropic.com/v1/messages",
        modelsEndpoint = "https://api.anthropic.com/v1/models",
        freeTier = false,
    ),

    /**
     * Google Gemini (BYO key). NATIVE Google format — the byte-for-byte twin of the Swift
     * `GeminiClient` (`Strand/AI/Providers/Gemini.swift`): `x-goog-api-key` auth, a `system_instruction`
     * + `contents`/`parts` body, and `candidates[].content.parts[].text` replies. [endpoint] is the base
     * models URL; the per-call `/<model>:generateContent` suffix is appended in [AiCoach.callGemini]
     * (kept literal so the `:` is never percent-encoded).
     *
     * The default is a PINNED id, not an alias (Ridge, 15 Nov 2026). The `-latest` aliases (#400) moved under the app:
     * `gemini-flash-latest` came to resolve to gemini-3.8-flash, a thinking model that took 140–210 s to start a Sage-sized
     * reply and ran into the read timeout, so Sage "didn't work". Measured the same day with a Sage-sized prompt:
     * gemini-3.5-flash-lite 1.2 s to the first token, 3.1-flash-lite 23 s, 3.6-flash 31 s. The aliases stay in the list
     * for anyone who wants them, and [AiCoach.fetchModels] still offers every `gemini*` id the key can use. The Swift
     * enum is not changed (Ridge is Android-only).
     */
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-3.5-flash-lite",
        models = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash",
            "gemini-3.8-flash",
            "gemini-flash-lite-latest",
            "gemini-flash-latest",
            "gemini-pro-latest",
        ),
        endpoint = "https://generativelanguage.googleapis.com/v1beta/models",
        modelsEndpoint = "https://generativelanguage.googleapis.com/v1beta/models",
        freeTier = true,
    ),

    /**
     * A generic OpenAI-compatible server the user points at — typically a LOCAL LLM such as
     * Ollama, LM Studio or llama.cpp (`http://localhost:11434/v1`), or any self-hosted gateway.
     * The endpoints here are placeholders: the real chat/models URLs are built at call time from
     * the user-set base URL (see [AiCoach] / [AiKeyStore.readCustomBaseUrl]). The API key is
     * optional — local servers usually need none.
     */
    CUSTOM(
        displayName = "Custom (OpenAI-compatible)",
        defaultModel = "",
        models = emptyList(),
        endpoint = "",
        modelsEndpoint = "",
    );

    companion object {
        /** Earlier Gemini defaults that are now too slow for Sage; a saved one is read as the current default. */
        internal val RETIRED_GEMINI_DEFAULTS = setOf("gemini-flash-latest")

        /** Resolve a provider by its persisted [name], falling back to [OPENAI]. */
        fun fromName(name: String?): AiProvider =
            entries.firstOrNull { it.name == name } ?: OPENAI
    }
}

enum class CustomAiAuthHeader(val displayName: String) {
    BEARER("Bearer"),
    X_API_KEY("x-api-key");

    companion object {
        fun fromName(name: String?): CustomAiAuthHeader =
            entries.firstOrNull { it.name == name } ?: BEARER
    }
}
