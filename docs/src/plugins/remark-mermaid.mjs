import { visit } from 'unist-util-visit';

/** remark 插件：把 ```mermaid 代码块转为 <pre class="mermaid"> HTML，绕过 Shiki */
export function remarkMermaid() {
  return (tree) => {
    visit(tree, 'code', (node, index, parent) => {
      if (node.lang !== 'mermaid') return;

      // 替换为 HTML 节点，Shiki 不会处理 html 类型
      const html = {
        type: 'html',
        value: `<pre class="mermaid">${node.value}</pre>`,
      };

      parent.children.splice(index, 1, html);
    });
  };
}
