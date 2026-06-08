import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Kairo',
  description: 'The Agent OS for Java',
  base: '/',
  ignoreDeadLinks: true,

  head: [
    ['link', { rel: 'icon', href: '/logo.png' }],
  ],

  locales: {
    en: {
      label: 'English',
      lang: 'en',
      link: '/en/',
      themeConfig: {
        nav: [
          {
            text: 'Guide',
            items: [
              { text: 'Introduction', link: '/en/guide/introduction' },
              { text: 'Getting Started', link: '/en/guide/getting-started' },
              { text: 'Architecture', link: '/en/guide/architecture' },
              { text: 'Features', link: '/en/guide/features' },
            ],
          },
          {
            text: 'Ecosystem',
            items: [
              { text: 'Kairo Code', link: '/en/kairo-code/' },
              { text: 'Kairo Assistant', link: '/en/kairo-assistant/' },
            ],
          },
          { text: 'API Reference', link: '/en/api/' },
          { text: 'Roadmap', link: '/en/guide/roadmap' },
        ],
        sidebar: {
          '/en/guide/': [
            {
              text: 'Guide',
              items: [
                { text: 'Introduction', link: '/en/guide/introduction' },
                { text: 'Getting Started', link: '/en/guide/getting-started' },
                { text: 'Architecture', link: '/en/guide/architecture' },
                { text: 'Features', link: '/en/guide/features' },
                { text: 'Roadmap', link: '/en/guide/roadmap' },
              ],
            },
            {
              text: 'Plugin System',
              items: [
                { text: 'Plugins', link: '/en/guide/plugins' },
                { text: 'Plugin Compatibility', link: '/en/guide/plugin-compatibility' },
              ],
            },
            {
              text: 'Governance',
              items: [
                { text: 'SPI Governance', link: '/en/guide/spi-governance' },
                { text: 'Exception Mapping', link: '/en/guide/exception-mapping' },
                { text: 'Security & Observability Schema', link: '/en/guide/security-observability-schema' },
              ],
            },
            {
              text: 'Upgrade',
              items: [
                { text: 'v0.6 → v0.7', link: '/en/guide/upgrade-v0.6-to-v0.7' },
                { text: 'v0.7 → v0.8', link: '/en/guide/upgrade-v0.7-to-v0.8' },
              ],
            },
          ],
          '/en/api/': [
            {
              text: 'API Reference',
              items: [
                { text: 'Overview', link: '/en/api/' },
                { text: 'Agent', link: '/en/api/Agent' },
                { text: 'ModelProvider', link: '/en/api/ModelProvider' },
                { text: 'ToolHandler', link: '/en/api/ToolHandler' },
                { text: 'Msg', link: '/en/api/Msg' },
                { text: 'KairoException', link: '/en/api/KairoException' },
              ],
            },
          ],
          '/en/kairo-code/': [
            {
              text: 'Kairo Code',
              items: [
                { text: 'Overview', link: '/en/kairo-code/' },
                { text: 'Getting Started', link: '/en/kairo-code/getting-started' },
                { text: 'Architecture', link: '/en/kairo-code/architecture' },
              ],
            },
          ],
          '/en/kairo-assistant/': [
            {
              text: 'Kairo Assistant',
              items: [
                { text: 'Overview', link: '/en/kairo-assistant/' },
              ],
            },
          ],
        },
        socialLinks: [
          { icon: 'github', link: 'https://github.com/CaptainGreenskin/kairo' },
        ],
      },
    },
    zh: {
      label: '中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: [
          {
            text: '指南',
            items: [
              { text: '简介', link: '/zh/guide/introduction' },
              { text: '快速开始', link: '/zh/guide/getting-started' },
              { text: '架构', link: '/zh/guide/architecture' },
              { text: '特性', link: '/zh/guide/features' },
            ],
          },
          {
            text: '生态',
            items: [
              { text: 'Kairo Code', link: '/zh/kairo-code/' },
              { text: 'Kairo Assistant', link: '/zh/kairo-assistant/' },
            ],
          },
          { text: 'API 参考', link: '/zh/api/' },
          { text: '路线图', link: '/zh/guide/roadmap' },
        ],
        sidebar: {
          '/zh/guide/': [
            {
              text: '指南',
              items: [
                { text: '简介', link: '/zh/guide/introduction' },
                { text: '快速开始', link: '/zh/guide/getting-started' },
                { text: '架构', link: '/zh/guide/architecture' },
                { text: '特性', link: '/zh/guide/features' },
                { text: '路线图', link: '/zh/guide/roadmap' },
              ],
            },
            {
              text: '插件系统',
              items: [
                { text: '插件', link: '/zh/guide/plugins' },
                { text: '插件兼容性', link: '/zh/guide/plugin-compatibility' },
              ],
            },
            {
              text: '治理',
              items: [
                { text: 'SPI 治理', link: '/zh/guide/spi-governance' },
                { text: '异常映射', link: '/zh/guide/exception-mapping' },
                { text: '安全与可观测性 Schema', link: '/zh/guide/security-observability-schema' },
              ],
            },
            {
              text: '升级',
              items: [
                { text: 'v0.6 → v0.7', link: '/zh/guide/upgrade-v0.6-to-v0.7' },
                { text: 'v0.7 → v0.8', link: '/zh/guide/upgrade-v0.7-to-v0.8' },
              ],
            },
          ],
          '/zh/api/': [
            {
              text: 'API 参考',
              items: [
                { text: '总览', link: '/zh/api/' },
                { text: 'Agent', link: '/zh/api/Agent' },
                { text: 'ModelProvider', link: '/zh/api/ModelProvider' },
                { text: 'ToolHandler', link: '/zh/api/ToolHandler' },
                { text: 'Msg', link: '/zh/api/Msg' },
                { text: 'KairoException', link: '/zh/api/KairoException' },
              ],
            },
          ],
          '/zh/kairo-code/': [
            {
              text: 'Kairo Code',
              items: [
                { text: '概览', link: '/zh/kairo-code/' },
                { text: '快速开始', link: '/zh/kairo-code/getting-started' },
                { text: '架构', link: '/zh/kairo-code/architecture' },
              ],
            },
          ],
          '/zh/kairo-assistant/': [
            {
              text: 'Kairo Assistant',
              items: [
                { text: '概览', link: '/zh/kairo-assistant/' },
              ],
            },
          ],
        },
        socialLinks: [
          { icon: 'github', link: 'https://github.com/CaptainGreenskin/kairo' },
        ],
      },
    },
  },
})
