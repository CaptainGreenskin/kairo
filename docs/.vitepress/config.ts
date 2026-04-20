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
          ],
        },
        socialLinks: [
          { icon: 'github', link: 'https://github.com/CaptainGreenskin/kairo' },
        ],
      },
    },
  },
})
