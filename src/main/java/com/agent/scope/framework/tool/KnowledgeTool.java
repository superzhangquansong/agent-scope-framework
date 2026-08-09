package com.agent.scope.framework.tool;

import com.agent.scope.framework.context.SessionContext;
import com.agent.scope.framework.hdl.port.HdlApiPort;
import com.agent.scope.framework.vo.ToolResultVO;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识库工具（特性 5：原生 RAG 能力）。
 *
 * <p>封装 HDL 百问百答知识库的检索能力。用户询问产品手册、安装指南、
 * 质保政策等问题时，LLM 通过本工具进行 RAG 语义检索。</p>
 *
 * @author zqs
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTool extends AbstractTool {

    /** HDL 业务 API 端口 */
    private final HdlApiPort hdlApiPort;

    /**
     * 知识库检索（RAG）。
     *
     * @param question       用户问题
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 为检索到的文档片段 JSON）
     */
    @Tool(name = "query_knowledge",
            description = "HDL 知识库语义检索（RAG）。对用户问题进行向量检索，返回最相关的文档片段。"
                    + "使用场景：适用于产品手册、安装指南、质保政策等知识查询，如'调光灯怎么接线'、'产品质保多久'、'RGB 灯带怎么配对'。"
                    + "禁止事项：禁止对设备控制、场景执行等实时操作类问题调用本工具，此类问题应调用对应的业务工具。",
            readOnly = true)
    public ToolResultVO queryKnowledge(
            @ToolParam(name = "question", required = true,
                    description = "用户的原始问题文本，必须原样传递，禁止改写、扩写或添加关键词。") String question,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[KnowledgeTool] 知识库检索: question={}, userId={}",
                question, sessionContext.getUserId());
        return hdlApiPort.queryKnowledge(question, sessionContext);
    }

    /**
     * 列出知识库所有文档。
     *
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 为文档列表 JSON）
     */
    @Tool(name = "list_knowledge_documents",
            description = "列出知识库所有文档。返回文档编码、名称、大小、上传时间等信息。使用场景：用户问'知识库有哪些文档'、'有哪些文件'时调用。",
            readOnly = true)
    public ToolResultVO listKnowledgeDocuments(RuntimeContext runtimeContext) {
        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[KnowledgeTool] 列出知识库文档: userId={}", sessionContext.getUserId());
        return hdlApiPort.listKnowledgeDocuments(sessionContext);
    }

    /**
     * 删除知识库文档。
     *
     * @param docCode        文档编码（唯一标识）
     * @param runtimeContext 运行时上下文（自动注入）
     * @return 工具结果 VO（data 含 deleted 字段）
     */
    @Tool(name = "delete_knowledge_document",
            description = "删除知识库文档。使用场景：用户说'删除文档XXX'、'移除文件XXX'时调用。参数来源要求：docCode 必须从 list_knowledge_documents 返回结果获取，禁止编造。",
            concurrencySafe = false)
    public ToolResultVO deleteKnowledgeDocument(
            @ToolParam(name = "docCode", required = true,
                    description = "文档编码（唯一标识）。示例：DOC_20260724_001。从list_knowledge_documents返回结果获取，禁止编造") String docCode,
            RuntimeContext runtimeContext) {

        SessionContext sessionContext = resolveSessionContext(runtimeContext);
        log.info("[KnowledgeTool] 删除知识库文档: docCode={}, userId={}",
                docCode, sessionContext.getUserId());
        return hdlApiPort.deleteKnowledgeDocument(docCode, sessionContext);
    }
}
