package com.agent.scope.framework;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AgentScope 应用启动入口。
 * <p>
 * 启用以下能力：
 * <ul>
 *   <li>{@link EnableAsync}：开启异步方法支持，使 {@code @Async} 标注的记录保存方法
 *       在独立线程池中执行，不阻塞 SSE 主流程</li>
 *   <li>{@link MapperScan}：扫描 Mapper 接口所在包，自动注册 MyBatis-Plus 代理</li>
 * </ul>
 * </p>
 *
 * @author zqs
 */
@EnableAsync
@MapperScan("com.agent.scope.framework.mapper")
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}