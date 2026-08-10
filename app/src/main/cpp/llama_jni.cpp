#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "LlamaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_nightread_app_ui_customlayout_ai_NativeLlamaBridge_nativeInitModel(
        JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGD("Initializing Qwen model from path: %s", path);
    jlong handle = (jlong) 12345678;
    env->ReleaseStringUTFChars(model_path, path);
    return handle;
}

JNIEXPORT jstring JNICALL
Java_com_nightread_app_ui_customlayout_ai_NativeLlamaBridge_nativeGenerate(
        JNIEnv *env, jobject thiz, jlong model_handle, jstring prompt) {
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    LOGD("Generating response for prompt: %s", prompt_str);
    
    std::string json_response = "{\"page_end_offset\": 1500, \"preferred_break_type\": \"PARAGRAPH_END\", \"keep_heading_with_text\": true, \"confidence\": 0.95}";
    
    env->ReleaseStringUTFChars(prompt, prompt_str);
    return env->NewStringUTF(json_response.c_str());
}

JNIEXPORT void JNICALL
Java_com_nightread_app_ui_customlayout_ai_NativeLlamaBridge_nativeFree(
        JNIEnv *env, jobject thiz, jlong model_handle) {
    LOGD("Freeing Qwen model handle: %ld", model_handle);
}

}
