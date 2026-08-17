package com.agent.scope.framework.service;

import com.agent.scope.framework.entity.SceneTemplate;
import com.agent.scope.framework.mapper.SceneTemplateMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 场景模板服务。
 *
 * <p>负责从 MySQL 加载预置场景模板并提供查询能力。模板数据在应用启动时预加载到内存，
 * 避免每次推荐都查询数据库。后续可通过 Nacos 配置覆盖或新增模板（Nacos 优先级高于 DB）。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>启动时从 DB 加载所有启用的场景模板到内存</li>
 *   <li>提供 getAllEnabledTemplates() 供推荐引擎使用</li>
 *   <li>提供 parseSpkArray() 解析 required_spks / optional_spks JSON 字段</li>
 * </ul>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SceneTemplateService {

    /**
     * 场景模板 Mapper
     */
    private final SceneTemplateMapper sceneTemplateMapper;

    /**
     * 内存缓存的启用模板列表（启动时加载，避免每次推荐查 DB）
     */
    private volatile List<SceneTemplate> cachedTemplates = Collections.emptyList();

    /**
     * 应用启动后从 DB 加载所有启用的场景模板到内存缓存。
     * <p>
     * 查询条件：enabled=1 且 deleted=0，按 priority 升序排列。
     * 若 DB 无数据则缓存为空列表，推荐引擎将返回空推荐结果。
     * </p>
     */
    @PostConstruct
    public void loadTemplates() {
        try {
            LambdaQueryWrapper<SceneTemplate> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SceneTemplate::getEnabled, 1)
                   .orderByAsc(SceneTemplate::getPriority);
            cachedTemplates = sceneTemplateMapper.selectList(wrapper);
            log.info("[SceneTemplate] 模板加载完成，数量: {}", cachedTemplates.size());
        } catch (Exception e) {
            log.error("[SceneTemplate] 模板加载失败，使用空列表降级: {}", e.getMessage(), e);
            cachedTemplates = Collections.emptyList();
        }
    }

    /**
     * 获取所有启用的场景模板（内存缓存）。
     *
     * @return 启用的场景模板列表，按 priority 升序
     */
    public List<SceneTemplate> getAllEnabledTemplates() {
        return cachedTemplates;
    }

    /**
     * 刷新模板缓存（供 Nacos 配置变更或手动刷新调用）。
     */
    public void refresh() {
        loadTemplates();
    }

    /**
     * 解析 spk JSON 数组字符串为 List。
     * <p>
     * 将 required_spks / optional_spks 字段（如 ["light.rgbcw","hvac.ac"]）
     * 解析为 List<String>。空字符串或解析失败返回空列表。
     * </p>
     *
     * @param spksJson spk JSON 数组字符串
     * @return spk 列表
     */
    public List<String> parseSpkArray(String spksJson) {
        if (spksJson == null || spksJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JSONArray arr = JSON.parseArray(spksJson);
            return arr.toList(String.class);
        } catch (Exception e) {
            log.warn("[SceneTemplate] spk 数组解析失败: {}", spksJson, e);
            return Collections.emptyList();
        }
    }
}
