import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Kairo',
  description: 'The Agent OS for Java',
  base: '/',

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
        },
        socialLinks: [
          { icon: 'github', link: 'https://github.com/CaptainGreenskin/kairo' },
        ],
      },
    },
  },
})
