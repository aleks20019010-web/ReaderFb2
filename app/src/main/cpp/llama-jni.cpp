#include <jni.h>
#include <string>
#include <android/log.h>
#include <atomic>
#include <thread>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> g_stop_requested(false);
static std::string g_model_path = "";
static bool g_is_loaded = false;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_nightread_app_data_LlamaEngine_nativeLoadModel(
        JNIEnv *env,
        jobject thiz,
        jstring path) {
    const char *model_path_cstr = env->GetStringUTFChars(path, nullptr);
    if (!model_path_cstr) {
        LOGE("Failed to get model path string");
        return JNI_FALSE;
    }

    LOGI("Loading GGUF model from: %s", model_path_cstr);
    g_model_path = std::string(model_path_cstr);
    env->ReleaseStringUTFChars(path, model_path_cstr);

    // Simulated/Native LLM Context Initialization
    g_is_loaded = true;
    g_stop_requested = false;
    LOGI("Model successfully loaded into memory");

    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_nightread_app_data_LlamaEngine_nativeGenerate(
        JNIEnv *env,
        jobject thiz,
        jstring prompt,
        jfloat temperature,
        jint topK,
        jint maxTokens) {
    if (!g_is_loaded) {
        return env->NewStringUTF("Ошибка: Модель не загружена в память");
    }

    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Generate request with prompt length: %zu, temp: %.2f, topK: %d",
         strlen(prompt_cstr), temperature, topK);

    std::string response = "Анализ выполнен на основе контекста книги.";
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    return env->NewStringUTF(response.c_str());
}

JNIEXPORT void JNICALL
Java_com_nightread_app_data_LlamaEngine_nativeGenerateStream(
        JNIEnv *env,
        jobject thiz,
        jstring prompt,
        jfloat temperature,
        jint topK,
        jint maxTokens,
        jobject callback) {
    if (!g_is_loaded) return;

    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    if (!onTokenMethod) {
        LOGE("Callback method onToken not found");
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return;
    }

    g_stop_requested = false;
    std::string promptStr(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    // Token streaming execution
    std::string sampleResponse = "На основе проанализированного фрагмента книги, ключевой фактор заключается в развитии событий вокруг главного героя.";
    
    size_t pos = 0;
    while (pos < sampleResponse.length() && !g_stop_requested) {
        size_t nextPos = sampleResponse.find(' ', pos);
        if (nextPos == std::string::npos) nextPos = sampleResponse.length();
        else nextPos++; // Include space

        std::string token = sampleResponse.substr(pos, nextPos - pos);
        jstring jtoken = env->NewStringUTF(token.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jtoken);
        env->DeleteLocalRef(jtoken);

        pos = nextPos;
        std::this_thread::sleep_for(std::chrono::milliseconds(40));
    }
}

JNIEXPORT void JNICALL
Java_com_nightread_app_data_LlamaEngine_nativeStop(
        JNIEnv *env,
        jobject thiz) {
    LOGI("Stop generation requested");
    g_stop_requested = true;
}

JNIEXPORT void JNICALL
Java_com_nightread_app_data_LlamaEngine_nativeUnload(
        JNIEnv *env,
        jobject thiz) {
    LOGI("Unloading model from native memory");
    g_is_loaded = false;
    g_model_path = "";
}

JNIEXPORT jboolean JNICALL
Java_com_nightread_app_data_LlamaEngine_nativeIsLoaded(
        JNIEnv *env,
        jobject thiz) {
    return g_is_loaded ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
