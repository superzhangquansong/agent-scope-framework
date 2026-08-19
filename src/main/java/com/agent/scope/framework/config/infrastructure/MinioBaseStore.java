package com.agent.scope.framework.config.infrastructure;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import io.minio.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 MinIO 对象存储的 {@link BaseStore} 实现。
 *
 * <p>替代官方 {@code RedisStore}，使工作区文件（MEMORY.md / memory/*.md /
 * sessions/*.log.jsonl 等）持久化到 MinIO 对象存储而非 Redis 内存。</p>
 *
 * <h3>对象 Key 映射规则</h3>
 * <pre>
 * path = ["memory", "alice"], key = "2026-08-18.md"
 * → MinIO objectName = "memory/alice/2026-08-18.md"
 * </pre>
 *
 * <h3>value Map 存储格式</h3>
 * <p>Map&lt;String, Object&gt; 序列化为 JSON 字符串作为对象内容存储，
 * version 存储在对象 user metadata 的 "version" 字段中。</p>
 *
 * <h3>与 RedisStore 的对比</h3>
 * <ul>
 *   <li>RedisStore：内存型存储，按 GB 计费最贵，适合高频低延迟热数据</li>
 *   <li>MinioBaseStore：对象存储，按 TB 计费最便宜，适合长期累积的冷温数据</li>
 * </ul>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
public class MinioBaseStore implements BaseStore {

    /** 对象元数据中的版本号字段名 */
    private static final String META_VERSION = "version";

    /** JSON 内容类型 */
    private static final String CONTENT_TYPE = "application/json";

    private final MinioClient minioClient;
    private final String bucket;

    /**
     * 构造 MinioBaseStore，自动创建存储桶（如不存在）。
     *
     * @param endpoint         MinIO 服务地址（如 http://59.41.255.150:9000）
     * @param accessKey        访问密钥
     * @param secretKey        秘密密钥
     * @param bucket           存储桶名称
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     */
    public MinioBaseStore(String endpoint, String accessKey, String secretKey, String bucket,
                          int connectTimeoutMs, int readTimeoutMs) {
        // 配置 HTTP 客户端超时，避免 MinIO 不可达时无限阻塞 Agent 主循环
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
        this.bucket = bucket;
        initBucket();
    }

    /**
     * 初始化存储桶：不存在则自动创建。
     */
    private void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[MinioBaseStore] 存储桶自动创建成功: bucket={}", bucket);
            } else {
                log.info("[MinioBaseStore] 存储桶已存在: bucket={}", bucket);
            }
        } catch (Exception e) {
            // 桶创建失败不阻断启动——后续读写操作会再次报错，此时日志已足够定位
            log.error("[MinioBaseStore] 存储桶初始化失败: bucket={}, error={}", bucket, e.getMessage(), e);
        }
    }

    /**
     * 将 path + key 映射为 MinIO objectName。
     * <pre>
     * path=["memory", "alice"], key="MEMORY.md" → "memory/alice/MEMORY.md"
     * </pre>
     * <p>
     * <b>防御性清理</b>：框架传入的 path 段可能带尾部斜杠（如 {@code "root/"}）或空段，
     * 统一去掉首尾空白与尾部斜杠后再拼接，避免产生 {@code root//AGENTS.md} 这类双斜杠路径，
     * 防止 MinIO 上出现奇怪目录且 search 前缀无法匹配。
     * </p>
     */
    private String toObjectName(List<String> path, String key) {
        StringBuilder sb = new StringBuilder();
        if (path != null && !path.isEmpty()) {
            for (String segment : path) {
                if (segment == null) {
                    continue;
                }
                // 去掉首尾空白与尾部斜杠（防御框架传 "root/" 这类段）
                String cleaned = segment.trim().replaceAll("/+$", "");
                if (!cleaned.isEmpty()) {
                    sb.append(cleaned).append("/");
                }
            }
        }
        sb.append(key);
        return sb.toString();
    }

    /**
     * 从 objectName 提取 key（最后一个 "/" 之后的部分）。
     */
    private String toKey(String objectName) {
        int idx = objectName.lastIndexOf('/');
        return idx >= 0 ? objectName.substring(idx + 1) : objectName;
    }

    /**
     * 从 objectName 提取 path 前缀（最后一个 "/" 之前的部分，按 "/" 分割）。
     */
    private List<String> toPath(String objectName) {
        int idx = objectName.lastIndexOf('/');
        if (idx <= 0) {
            return Collections.emptyList();
        }
        String prefix = objectName.substring(0, idx);
        String[] segments = prefix.split("/");
        List<String> path = new ArrayList<>(segments.length);
        for (String seg : segments) {
            if (!seg.isEmpty()) {
                path.add(seg);
            }
        }
        return path;
    }

    // ==================== BaseStore 接口实现 ====================

    /**
     * 从 MinIO 读取文件。
     * <p>读取 objectName 对应的对象，反序列化 JSON 为 Map，
     * 从 user metadata 提取 version，组装为 StoreItem 返回。</p>
     *
     * @param path 命名空间路径段列表
     * @param key  文件名
     * @return StoreItem（含 key/value/version），不存在返回 null
     */
    @Override
    public StoreItem get(List<String> path, String key) {
        String objectName = toObjectName(path, key);
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
            if (is == null) {
                return null;
            }
            // 读取对象内容
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> value = JSON.parseObject(json, Map.class);

            // 从响应头获取 version（user metadata）
            // MinIO Java SDK 在 GetObjectResponse 的 headers 中返回 user metadata
            // 此处通过 StatObject 获取 version 更可靠，但为简化实现，默认 version=0
            // 若 putIfVersion 需要精确版本号，可通过 statObject 获取
            long version = extractVersion(value);

            return new StoreItem(key, value, version);
        } catch (Exception e) {
            // 对象不存在等异常视为 null（框架的 readWithOverride 会降级到本地磁盘）
            log.debug("[MinioBaseStore] 读取对象失败（可能不存在）: objectName={}, error={}",
                    objectName, e.getMessage());
            return null;
        }
    }

    /**
     * 从 value Map 中提取 version 字段（若存在）。
     * <p>putIfVersion 写入时会把 version 存入 value Map，
     * get 读取时从这里恢复版本号。</p>
     */
    private long extractVersion(Map<String, Object> value) {
        if (value != null && value.containsKey(META_VERSION)) {
            Object v = value.get(META_VERSION);
            if (v instanceof Number n) {
                return n.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(v));
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * 向 MinIO 写入文件。
     * <p>将 value Map 序列化为 JSON 字符串，作为对象内容写入 MinIO。</p>
     *
     * @param path  命名空间路径段列表
     * @param key   文件名
     * @param value 文件内容（Map 格式）
     */
    @Override
    public void put(List<String> path, String key, Map<String, Object> value) {
        String objectName = toObjectName(path, key);
        try {
            String json = JSON.toJSONString(value);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .stream(is, (long) bytes.length, -1)
                                .contentType(CONTENT_TYPE)
                                .build());
            }
            log.debug("[MinioBaseStore] 写入对象成功: objectName={}, size={}bytes",
                    objectName, bytes.length);
        } catch (Exception e) {
            log.error("[MinioBaseStore] 写入对象失败: objectName={}, error={}",
                    objectName, e.getMessage(), e);
            throw new RuntimeException("MinIO put 失败: " + objectName, e);
        }
    }

    /**
     * 乐观锁写入：仅当当前版本号等于 expectedVersion 时才写入。
     * <p>
     * MinIO 无原生 CAS，通过先 get 当前 version 比较后再 put 实现。
     * 存在竞态窗口（get 和 put 之间可能被其他线程修改），
     * 但工作区文件写入频率极低（记忆 flush 30 分钟一次），实际竞态概率可忽略。
     * </p>
     *
     * @param path           命名空间路径段列表
     * @param key            文件名
     * @param value          文件内容
     * @param expectedVersion 期望的当前版本号
     * @return true=写入成功，false=版本不匹配
     */
    @Override
    public boolean putIfVersion(List<String> path, String key,
                                 Map<String, Object> value, long expectedVersion) {
        StoreItem current = get(path, key);
        long currentVersion = current != null ? current.version() : 0L;

        if (currentVersion != expectedVersion) {
            log.debug("[MinioBaseStore] 乐观锁版本不匹配: objectName={}, expected={}, actual={}",
                    toObjectName(path, key), expectedVersion, currentVersion);
            return false;
        }

        // 写入新版本（version + 1 存入 value Map，下次 get 时恢复）
        Map<String, Object> newValue = new java.util.LinkedHashMap<>(value);
        newValue.put(META_VERSION, currentVersion + 1);
        put(path, key, newValue);
        return true;
    }

    /**
     * 列出指定路径下的文件。
     * <p>使用 MinIO listObjects API 按前缀搜索对象，返回 StoreItem 列表。</p>
     *
     * @param path   命名空间路径段列表
     * @param offset 分页偏移量
     * @param limit  最大返回条数
     * @return StoreItem 列表（仅含 key，value 需按需 get）
     */
    @Override
    public List<StoreItem> search(List<String> path, int offset, int limit) {
        // 构建搜索前缀：path 段去尾部斜杠后用 "/" 连接 + "/"（与 toObjectName 保持一致，避免双斜杠）
        StringBuilder prefixBuilder = new StringBuilder();
        if (path != null) {
            for (String segment : path) {
                if (segment == null) {
                    continue;
                }
                String cleaned = segment.trim().replaceAll("/+$", "");
                if (!cleaned.isEmpty()) {
                    prefixBuilder.append(cleaned).append("/");
                }
            }
        }
        String prefix = prefixBuilder.toString();

        List<StoreItem> results = new ArrayList<>();
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build());

            int skipped = 0;
            int collected = 0;
            for (Result<Item> result : objects) {
                if (collected >= limit) {
                    break;
                }
                Item item = result.get();
                String objectName = item.objectName();

                // 跳过目录占位符（以 "/" 结尾的对象）
                if (objectName.endsWith("/")) {
                    continue;
                }

                // 分页偏移
                if (skipped < offset) {
                    skipped++;
                    continue;
                }

                String key = toKey(objectName);
                // search 仅返回 key 和空 value（按需 get 获取完整内容，避免大量 IO）
                results.add(new StoreItem(key, Collections.emptyMap(), 0L));
                collected++;
            }
        } catch (Exception e) {
            log.error("[MinioBaseStore] 搜索对象失败: prefix={}, error={}",
                    prefix, e.getMessage(), e);
        }
        return results;
    }

    /**
     * 删除 MinIO 中的文件。
     *
     * @param path 命名空间路径段列表
     * @param key  文件名
     */
    @Override
    public void delete(List<String> path, String key) {
        String objectName = toObjectName(path, key);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
            log.debug("[MinioBaseStore] 删除对象成功: objectName={}", objectName);
        } catch (Exception e) {
            log.error("[MinioBaseStore] 删除对象失败: objectName={}, error={}",
                    objectName, e.getMessage(), e);
        }
    }
}
