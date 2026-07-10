package com.nightread.app.data

data class AiModel(
    val id: String,
    val name: String,
    val sizeStr: String,
    val description: String,
    val url: String,
    val fileName: String
)

val AVAILABLE_AI_MODELS = listOf(
    AiModel(
        id = "gemma-2b",
        name = "Gemma 2B IT",
        sizeStr = "~1.5 ГБ",
        description = "Быстрая базовая модель",
        url = "https://huggingface.co/google/gemma-2b-it-GGUF/resolve/main/gemma-2b-it.Q4_K_M.gguf",
        fileName = "gemma_2b.gguf"
    ),
    AiModel(
        id = "qwen-1.5b",
        name = "Qwen2.5 1.5B Instruct",
        sizeStr = "~1.1 ГБ",
        description = "Быстрая и качественная модель",
        url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        fileName = "qwen2.5_1.5b.gguf"
    ),
    AiModel(
        id = "llama-3.2-3b",
        name = "Llama 3.2 3B Instruct",
        sizeStr = "~2 ГБ",
        description = "Качественная и точная модель",
        url = "https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct-GGUF/resolve/main/llama-3.2-3b-instruct-q4_k_m.gguf",
        fileName = "llama3.2_3b.gguf"
    )
)
