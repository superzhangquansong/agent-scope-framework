package com.agent.scope.framework.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author zqs
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatStreamDTO implements Serializable {
    @NotBlank(message = "会话信息不能为空")
    private String sessionId;
    @NotBlank(message = "用户信息不能为空")
    private String userId;
    private String houseId;
    @NotBlank(message = "信息不能为空")
    private String userMessage;
    private String accessToken;
}