#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex g_mutex;
static volatile bool g_stop_generation = false;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_jugaad_core_llmruntime_jni_LlamaJni_nativeLoadModel(
    JNIEnv* env, jobject, jstring modelPath, jint nCtx, jint nThreads) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU only on Android

    llama_model* model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load model");
        return 0;
    }

    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jlong JNICALL
Java_com_jugaad_core_llmruntime_jni_LlamaJni_nativeCreateContext(
    JNIEnv* env, jobject, jlong modelPtr, jint nCtx) {

    llama_model* model = reinterpret_cast<llama_model*>(modelPtr);
    if (!model) return 0;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx;
    ctx_params.n_threads = 6;
    ctx_params.n_threads_batch = 6;

    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        return 0;
    }

    LOGI("Context created, n_ctx=%d", nCtx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_jugaad_core_llmruntime_jni_LlamaJni_nativeGenerate(
    JNIEnv* env, jobject, jlong ctxPtr, jlong modelPtr, jstring prompt,
    jint maxTokens, jfloat temperature, jfloat topP, jint topK, jfloat repPenalty) {

    std::lock_guard<std::mutex> lock(g_mutex);

    llama_context* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    llama_model*   model = reinterpret_cast<llama_model*>(modelPtr);
    if (!ctx || !model) return env->NewStringUTF("");

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_std(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // Tokenize
    const int n_vocab = llama_vocab_n_tokens(llama_model_get_vocab(model));
    std::vector<llama_token> tokens;
    tokens.resize(prompt_std.size() + 16);
    int n_tokens = llama_tokenize(
        llama_model_get_vocab(model),
        prompt_std.c_str(), prompt_std.size(),
        tokens.data(), tokens.size(),
        /*add_special=*/true, /*parse_special=*/false
    );
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        llama_tokenize(
            llama_model_get_vocab(model),
            prompt_std.c_str(), prompt_std.size(),
            tokens.data(), tokens.size(),
            true, false
        );
        n_tokens = -n_tokens;
    }
    tokens.resize(n_tokens);

    // Decode prompt
    llama_memory_clear(llama_get_memory(ctx), false);
    if (llama_decode(ctx, llama_batch_get_one(tokens.data(), tokens.size()))) {
        LOGE("Prompt decode failed");
        return env->NewStringUTF("");
    }

    // Modern sampler chain (post-b3900 API)
    struct llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(64, repPenalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result;
    int n_cur = n_tokens;
    const int n_max = n_cur + maxTokens;
    char piece[256];

    while (n_cur < n_max) {
        llama_token id = llama_sampler_sample(sampler, ctx, -1);
        llama_sampler_accept(sampler, id);

        if (llama_vocab_is_eog(llama_model_get_vocab(model), id)) break;

        int n = llama_token_to_piece(
            llama_model_get_vocab(model), id, piece, sizeof(piece), 0, false
        );
        if (n < 0) break;
        result.append(piece, n);

        n_cur++;
        if (llama_decode(ctx, llama_batch_get_one(&id, 1))) break;
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_jugaad_core_llmruntime_jni_LlamaJni_nativeGenerateStream(
    JNIEnv* env, jobject, jlong ctxPtr, jlong modelPtr, jstring prompt,
    jint maxTokens, jfloat temperature, jfloat topP, jint topK, jfloat repPenalty, jobject callback) {

    std::lock_guard<std::mutex> lock(g_mutex);

    llama_context* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    llama_model*   model = reinterpret_cast<llama_model*>(modelPtr);
    if (!ctx || !model) return;

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_std(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // Tokenize
    std::vector<llama_token> tokens;
    tokens.resize(prompt_std.size() + 16);
    int n_tokens = llama_tokenize(
        llama_model_get_vocab(model),
        prompt_std.c_str(), prompt_std.size(),
        tokens.data(), tokens.size(),
        true, false
    );
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        llama_tokenize(llama_model_get_vocab(model), prompt_std.c_str(), prompt_std.size(), tokens.data(), tokens.size(), true, false);
        n_tokens = -n_tokens;
    }
    tokens.resize(n_tokens);

    // KV Cache Management (tag b3584 style)
    llama_memory_clear(llama_get_memory(ctx), false);

    if (llama_decode(ctx, llama_batch_get_one(tokens.data(), tokens.size()))) {
        LOGE("Prompt decode failed");
        return;
    }

    struct llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(64, repPenalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    g_stop_generation = false;
    int n_cur = n_tokens;
    const int n_max = n_cur + maxTokens;
    char piece[256];

    while (n_cur < n_max) {
        if (g_stop_generation) {
            LOGI("Generation interrupted by stop flag");
            break;
        }

        llama_token id = llama_sampler_sample(sampler, ctx, -1);
        llama_sampler_accept(sampler, id);

        if (llama_vocab_is_eog(llama_model_get_vocab(model), id)) break;

        int n = llama_token_to_piece(llama_model_get_vocab(model), id, piece, sizeof(piece), 0, false);
        if (n > 0) {
            jstring token_str = env->NewStringUTF(std::string(piece, n).c_str());
            env->CallObjectMethod(callback, invokeMethod, token_str);
            env->DeleteLocalRef(token_str);
        }

        n_cur++;
        if (llama_decode(ctx, llama_batch_get_one(&id, 1))) break;
    }

    llama_sampler_free(sampler);
}

JNIEXPORT void JNICALL
Java_com_jugaad_core_llmruntime_jni_LlamaJni_nativeStopGeneration(
    JNIEnv*, jobject) {
    g_stop_generation = true;
}

JNIEXPORT void JNICALL
Java_com_jugaad_core_llmruntime_jni_LlamaJni_nativeReleaseContext(
    JNIEnv* env, jobject, jlong ctxPtr) {
    llama_context* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    if (ctx) llama_free(ctx);
}

JNIEXPORT void JNICALL
Java_com_jugaad_core_llmruntime_jni_LlamaJni_nativeFreeModel(
    JNIEnv* env, jobject, jlong modelPtr) {
    llama_model* model = reinterpret_cast<llama_model*>(modelPtr);
    if (model) llama_model_free(model);
}

}
