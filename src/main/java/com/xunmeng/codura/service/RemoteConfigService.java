package com.xunmeng.codura.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xunmeng.codura.system.pojo.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xunmeng.codura.net.HttpClient;
import com.xunmeng.codura.service.constant.ProviderType;
import com.xunmeng.codura.setting.provider.*;
import com.xunmeng.codura.setting.state.SystemInfoStateService;
import okhttp3.Response;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.xunmeng.codura.service.RemoteConfigService.ConfigPaths.*;


public class RemoteConfigService {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, Object> cache = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastFetchTime = new ConcurrentHashMap<>();
    private static final long Provider_CACHE_TTL = 600_000L; // 600 秒
    private static final long LLmGate_CACHE_TTL = 604_800_000L; // 一周

    // 配置接口路径常量
    public static class ConfigPaths {
        public static final String BASE_URL = "http://127.0.0.1:8080/"; //网关后端

        public static final String CHAT_CONFIG_PATH = "/api/chatConfig/user/";
        public static final String FIM_CONFIG_PATH = "/api/fimConfig/user/";
        public static final String MODEL_CONFIG_PATH = "/api/modelConfig/user/";

        public static final String Public_CHAT_CONFIG_PATH = "/api/chatConfig/public";
        public static final String Public_FIM_CONFIG_PATH = "/api/fimConfig/public";
        public static final String Public_MODEL_CONFIG_PATH = "/api/modelConfig/public";

        public static final String LLMGATE_LOG_PATH = BASE_URL+"llmgate/llm-log/list";

        public static final String LLMGATE_CONFIG_PATH = BASE_URL + "config/llmgate";
    }

    // ========================== 公共方法 ==========================



    //获取后管配置
    public static <T> T fetchConfig(String path, Function<JsonNode, T> parser) {
        try {
            User user = SystemInfoStateService.settings().getUserInfoProvider().getUser();
            String baseUrl = SystemInfoStateService.settings().getSystemInfoProvider().getRequestBaseUrl();

            String fullUrl;
            Map<String, String> headers = new HashMap<>();

            if (user != null && user.getName() != null && !user.getName().trim().isEmpty()
                    && user.getToken() != null && !user.getToken().trim().isEmpty()) {
                // 已登录用户：请求私有配置
                fullUrl = baseUrl + path + user.getName();
                headers.put("Authorization", "Bearer " + user.getToken());
            } else {
                // 未登录：请求公共配置
                if(path.contains("chatConfig")){
                    fullUrl = baseUrl + Public_CHAT_CONFIG_PATH;
                }else if(path.contains("fimConfig")){
                    fullUrl = baseUrl + Public_FIM_CONFIG_PATH;
                }else if(path.contains("modelConfig")){
                    fullUrl = baseUrl + Public_MODEL_CONFIG_PATH;
                }else {
                    fullUrl = baseUrl + path;
                }
                // 公共接口不需要 token
            }

            Future<Response> future = HttpClient.request("GET", fullUrl, headers, null, null);
            Response response = future.get(); // 阻塞获取

            if (response.isSuccessful()) {
                String json = response.body().string();
                JsonNode root = mapper.readTree(json);
                if (root.has("code") && root.get("code").asInt() == 200) {
                    JsonNode data = root.get("data");
                    return parser.apply(data);
                } else {
                    System.err.println("接口返回失败 code: " + root.get("code"));
                }
            } else {
                System.err.println("HTTP 请求失败：" + response.code() + " " + response.message());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static <T> T fetchConfig(String path, Class<T> clazz) {
        return fetchConfig(path, node -> {
            try {
                return mapper.treeToValue(node, clazz);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public static <T> T fetchCachedConfig(String cacheKey, long ttlMillis, Supplier<T> supplier) {
        long now = System.currentTimeMillis();
        if (!cache.containsKey(cacheKey) || now - lastFetchTime.getOrDefault(cacheKey, 0L) > ttlMillis) {
            T value = supplier.get();
            if (value != null) {
                cache.put(cacheKey, value);
                lastFetchTime.put(cacheKey, now);
            }
        }
        return (T) cache.get(cacheKey);
    }


    // ========================== 具体配置获取 ==========================

    public static CompletionConfigProvider fetchCompletionConfig() {
        return fetchConfig(ConfigPaths.FIM_CONFIG_PATH, CompletionConfigProvider.class);
    }

    public static CompletionConfigProvider fetchCompletionConfigCached() {
        return fetchCachedConfig("completionConfig", Provider_CACHE_TTL,RemoteConfigService::fetchCompletionConfig);
    }

    public static ChatConfigProvider fetchChatConfig() {
        return fetchConfig(ConfigPaths.CHAT_CONFIG_PATH, ChatConfigProvider.class);
    }

    public static ChatConfigProvider fetchChatConfigCached() {
        return fetchCachedConfig("chatConfig", Provider_CACHE_TTL,RemoteConfigService::fetchChatConfig);
    }

    public static List<ModelProvider> fetchModelProviders() {
        return fetchConfig(ConfigPaths.MODEL_CONFIG_PATH, node -> {
            List<ModelProvider> providers = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    int businessType = item.path("businessType").asInt();
                    try {
                        switch (ProviderType.from(businessType)) {
                            case CHAT:
                                ChatModelProvider chat = mapper.treeToValue(item, ChatModelProvider.class);
                                providers.add(chat);
                                break;
                            case FIM:
                                FimModelProvider fim = mapper.treeToValue(item, FimModelProvider.class);
                                providers.add(fim);
                                break;
                            default:
                                System.err.println("未知 businessType: " + businessType);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return providers;
        });
    }

    public static ChatModelProvider getChatModelProvider() {
        return fetchModelProviders().stream()
                .filter(p -> p instanceof ChatModelProvider)
                .map(p -> (ChatModelProvider) p)
                .findFirst()
                .orElse(null);
    }

    public static FimModelProvider getFimModelProvider() {
        return fetchModelProviders().stream()
                .filter(p -> p instanceof FimModelProvider)
                .map(p -> (FimModelProvider) p)
                .findFirst()
                .orElse(null);
    }

    public static ChatModelProvider getChatModelProviderCached() {
        return fetchCachedConfig("chatModelProvider", Provider_CACHE_TTL,RemoteConfigService::getChatModelProvider);
    }

    public static FimModelProvider getFimModelProviderCached() {
        return fetchCachedConfig("fimModelProvider",Provider_CACHE_TTL, RemoteConfigService::getFimModelProvider);
    }

    //==============================网关=================
    //获取网关配置
    public static LlmGateConfigProvider fetchLlmGateConfig() {
        try {

            Map<String, String> headers = new HashMap<>();

            Future<Response> future = HttpClient.request("GET", LLMGATE_CONFIG_PATH, headers, null, null);
            Response response = future.get(); // 阻塞获取

            if (response.isSuccessful()) {
                String json = response.body().string();
                JsonNode root = mapper.readTree(json);
                return mapper.treeToValue(root, LlmGateConfigProvider.class);

            } else {
                System.err.println("HTTP 请求失败：" + response.code() + " " + response.message());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    public static LlmGateConfigProvider fetchLlmGateConfigCached() {
        return fetchCachedConfig("llmGateConfig",LLmGate_CACHE_TTL, RemoteConfigService::fetchLlmGateConfig);
    }

    public static LlmGateLogProvider fetchLogByRequestId(String requestId) {
        User user = SystemInfoStateService.settings().getUserInfoProvider().getUser();
        String token = (user != null && user.getToken() != null && !user.getToken().trim().isEmpty())
                ? user.getToken()
                : null;

        try {
            Map<String, String> headers = new HashMap<>();
            if (token != null) {
                headers.put("Authorization", "Bearer " + token);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("requestId", requestId);

            Future<Response> future = HttpClient.request("GET", LLMGATE_LOG_PATH, headers, params, null);
            Response response = future.get();

            if (response.isSuccessful()) {
                String json = response.body().string();
                JsonNode root = mapper.readTree(json);
                if (root.has("code") && root.get("code").asInt() == 200) {
                    JsonNode rows = root.get("rows");
                    if (rows != null && rows.isArray() && rows.size() > 0) {
                        return mapper.treeToValue(rows.get(0), LlmGateLogProvider.class);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}

