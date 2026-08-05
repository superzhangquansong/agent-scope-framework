package com.agent.scope.framework.hdl;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HDL API 签名工具
 *
 * <p>完全对标 hdl-ai-miniprogram/skills/home-skill/utils/sign.js 的签名算法：
 * <ol>
 *   <li>过滤参数：排除 sign 字段、null/空字符串值、非基础类型（仅保留 String/Number/Boolean）</li>
 *   <li>按 key 的 ASCII 码升序排序</li>
 *   <li>拼接为 key=value 用 & 连接</li>
 *   <li>尾部追加 appSecret</li>
 *   <li>MD5 摘要转小写（UTF-8 编码，解决中文参数签名不一致）</li>
 * </ol>
 *
 * @author zqs
 * @since 1.0.0
 */
@Component
public class HdlSignUtil {

    /**
     * 计算签名
     *
     * @param params    请求参数（含业务参数 + appKey + timestamp）
     * @param appSecret 应用密钥
     * @return MD5 小写签名
     */
    public String getSign(Map<String, Object> params, String appSecret) {
        if (params == null || params.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            return "";
        }

        // 第一步：参数过滤，仅保留基础类型且非空字段
        List<String> filteredKeys = new ArrayList<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            // 排除 sign 字段
            if ("sign".equals(key)) {
                continue;
            }
            // 排除 null/空字符串
            if (val == null) {
                continue;
            }
            if (val instanceof String s && s.isEmpty()) {
                continue;
            }
            // 仅保留基础类型：String / Number / Boolean
            if (val instanceof CharSequence || val instanceof Number || val instanceof Boolean) {
                filteredKeys.add(key);
            }
        }

        // 第二步：按 key ASCII 升序排序
        Collections.sort(filteredKeys);

        // 第三步：拼接 key=value 用 & 连接
        StringBuilder paramStr = new StringBuilder();
        for (int i = 0; i < filteredKeys.size(); i++) {
            if (i > 0) {
                paramStr.append('&');
            }
            String k = filteredKeys.get(i);
            paramStr.append(k).append('=').append(params.get(k));
        }

        // 第四步：追加密钥
        String signStr = paramStr.toString() + appSecret;

        // 第五步：MD5 小写（UTF-8 编码）
        return DigestUtils.md5Hex(signStr.getBytes(StandardCharsets.UTF_8)).toLowerCase();
    }
}
