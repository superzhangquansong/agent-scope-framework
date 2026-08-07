package com.agent.scope.framework.controller;

import com.agent.scope.framework.config.ConfigVersionConfig.ConfigVersionManager;
import com.agent.scope.framework.constant.BusinessConst;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置版本管理控制器（特性四十六）。
 * <p>
 * 通过 Nacos OpenAPI 暴露配置版本查询与回滚能力，支持：
 * <ul>
 *   <li>查询配置历史版本列表（分页）</li>
 *   <li>获取特定版本的配置内容</li>
 *   <li>回滚到指定历史版本</li>
 *   <li>查询当前运行环境信息</li>
 * </ul>
 * </p>
 *
 * <p><b>接口列表</b>：
 * <ul>
 *   <li>GET  /api/config/version/history         - 查询历史版本列表（pageNo, pageSize）</li>
 *   <li>GET  /api/config/version/{nid}            - 获取特定版本内容</li>
 *   <li>POST /api/config/version/{nid}/rollback   - 回滚到指定版本</li>
 *   <li>GET  /api/config/version/environment      - 获取当前环境信息</li>
 * </ul>
 * </p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/config/version")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "scope.agentscope.config-version", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConfigVersionController {

    /** Jackson ObjectMapper（用于解析 Nacos 返回的 JSON） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConfigVersionManager configVersionManager;

    /**
     * 查询配置历史版本列表。
     *
     * @param pageNo   页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @return 历史版本列表 JSON
     */
    @GetMapping("/history")
    public Map<String, Object> queryHistory(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[ConfigVersion] 查询历史版本: pageNo={}, pageSize={}", pageNo, pageSize);
        String result = configVersionManager.queryHistory(pageNo, pageSize);
        return buildResponse(result);
    }

    /**
     * 获取特定版本的配置内容。
     *
     * @param nid 历史版本 ID
     * @return 版本详情 JSON
     */
    @GetMapping("/{nid}")
    public Map<String, Object> getVersion(@PathVariable String nid) {
        log.info("[ConfigVersion] 获取版本详情: nid={}", nid);
        String result = configVersionManager.getVersion(nid);
        return buildResponse(result);
    }

    /**
     * 回滚到指定历史版本。
     *
     * @param nid 历史版本 ID
     * @return 回滚结果 JSON
     */
    @PostMapping("/{nid}/rollback")
    public Map<String, Object> rollback(@PathVariable String nid) {
        log.info("[ConfigVersion] 回滚配置版本: nid={}", nid);
        String result = configVersionManager.rollback(nid);
        return buildResponse(result);
    }

    /**
     * 获取当前运行环境信息。
     *
     * @return 环境信息
     */
    @GetMapping("/environment")
    public Map<String, Object> getEnvironmentInfo() {
        log.info("[ConfigVersion] 获取环境信息");
        String envInfo = configVersionManager.getEnvironmentInfo();
        Map<String, Object> data = new HashMap<>(2);
        data.put("environment", envInfo);
        Map<String, Object> response = new HashMap<>(4);
        response.put(BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK);
        response.put(BusinessConst.RESPONSE_KEY_DATA, data);
        return response;
    }

    /**
     * 构建统一响应格式。
     * <p>
     * 将 Nacos 返回的 JSON 字符串解析后包装到统一响应体中。
     * 若解析失败则直接返回原始字符串。
     * </p>
     *
     * @param nacosResponse Nacos 返回的 JSON 字符串
     * @return 统一响应 Map
     */
    private Map<String, Object> buildResponse(String nacosResponse) {
        Map<String, Object> response = new HashMap<>(4);
        response.put(BusinessConst.RESPONSE_KEY_CODE, BusinessConst.HTTP_OK);
        try {
            Object parsed = OBJECT_MAPPER.readValue(nacosResponse, Object.class);
            response.put(BusinessConst.RESPONSE_KEY_DATA, parsed);
        } catch (Exception e) {
            // JSON 解析失败时返回原始字符串
            log.warn("[ConfigVersion] Nacos 响应 JSON 解析失败，返回原始字符串: {}", e.getMessage());
            response.put(BusinessConst.RESPONSE_KEY_DATA, nacosResponse);
        }
        return response;
    }
}
