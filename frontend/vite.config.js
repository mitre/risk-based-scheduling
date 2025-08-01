// Plugins
import vue from '@vitejs/plugin-vue'
import vuetify, { transformAssetUrls } from 'vite-plugin-vuetify'

// Utilities
import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'

const devServer = {
  port: 3000,
  host: '0.0.0.0',
  proxy: {
    '^/api/case-gen': {
      target: 'http://localhost:6500',
      rewrite: (path) => path.replace(/^\/api\/case-gen/, ''),
      autoRewrite: true,
    },
    '^/api/scheduler': {
      target: 'http://localhost:4000',
      rewrite: (path) => path.replace(/^\/api\/scheduler/, ''),
      autoRewrite: true,
    },
    '^/api/simulation': {
      target: 'http://localhost:7000',
      rewrite: (path) => path.replace(/^\/api\/simulation/, ''),
      autoRewrite: true,
    },
  }
};

const previewServer = {
  port: 3000,
  host: '0.0.0.0',
  proxy: {
    '^/api/case-gen': {
      target: 'http://case_gen:6500',
      rewrite: (path) => path.replace(/^\/api\/case-gen/, ''),
      autoRewrite: true,
    },
    '^/api/scheduler': {
      target: 'http://sched:4000',
      rewrite: (path) => path.replace(/^\/api\/scheduler/, ''),
      autoRewrite: true,
    },
    '^/api/simulation': {
      target: 'http://risk_based_sim:7000',
      rewrite: (path) => path.replace(/^\/api\/simulation/, ''),
      autoRewrite: true,
    },
  }
};

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue({ 
      template: { transformAssetUrls }
    }),
    // https://github.com/vuetifyjs/vuetify-loader/tree/next/packages/vite-plugin
    vuetify({
      autoImport: true,
      styles: {
        configFile: 'src/styles/settings.scss',
      },
    }),
  ],
  define: { 'process.env': {} },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
    extensions: [
      '.js',
      '.json',
      '.jsx',
      '.mjs',
      '.ts',
      '.tsx',
      '.vue',
    ],
  },
  server: devServer,
  preview: previewServer,
})
