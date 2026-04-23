# Aria Language Website

- Aria 脚本语言的文档站点。
- 文档内容含AI自动生成内容，如有问题可提交PR或issues

## 技术栈

- [Astro](https://astro.build) — 静态站点生成
- [Tailwind CSS](https://tailwindcss.com) — 样式
- [Mermaid](https://mermaid.js.org) — 图表渲染（客户端）
- [Shiki](https://shiki.style) — 代码高亮（含自定义 Aria 语法）

## 本地开发

```bash
npm install
npm run dev
```

## 构建部署

```bash
npm run build
npm run preview   # 本地预览构建产物
```

构建产物在 `dist/` 目录，纯静态文件，可部署到 Vercel / Netlify / GitHub Pages 等任意静态托管。

## 文档编辑

文档源文件在 `src/content/docs/`，Markdown 格式，修改后重新构建即可。

支持 ` ```aria ` 代码块语法高亮和 ` ```mermaid ` 图表渲染。

## 许可证

本项目采用 [CC0 1.0 Universal](LICENSE) 许可证。
