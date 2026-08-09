import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
// K8s 模式下通过 VITE_BASE 设置子路径（如 /hdl-ai-assistant/），确保静态资源路径正确
// 前端直接调后端 API（API_BASE 由 VITE_API_BASE 环境变量配置，默认 http://localhost:8686/hdl-ai-assistant）
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    base: env.VITE_BASE || '/',
  }
})
