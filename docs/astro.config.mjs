import { defineConfig } from 'astro/config';
import tailwind from '@astrojs/tailwind';
import { remarkMermaid } from './src/plugins/remark-mermaid.mjs';
import { readFileSync } from 'fs';

const ariaGrammar = JSON.parse(
  readFileSync('./src/plugins/aria-grammar.json', 'utf-8')
);

// 自定义域名 aria.fan：site 指向域名，不需要 base
// 通过 docs/public/CNAME 文件让 GitHub Pages 识别自定义域名
export default defineConfig({
  site: 'https://aria.fan',
  trailingSlash: 'ignore',
  integrations: [tailwind()],
  output: 'static',
  markdown: {
    remarkPlugins: [remarkMermaid],
    shikiConfig: {
      theme: 'material-theme-palenight',
      langs: [ariaGrammar],
    },
  },
});
