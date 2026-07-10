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
        url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        fileName = "llama3.2_3b.gguf"
    )
)
