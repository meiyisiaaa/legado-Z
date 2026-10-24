import { createApp } from 'vue'
import App from './App.vue'
import router from '@/router'
import store from '@/store'
import { useBookStore } from '@/store'
import '@/assets/app.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

const app = createApp(App).use(store).use(router)
const bookStore = useBookStore()
watch(
  () => bookStore.isNight,
  isNight => {
    document.documentElement.classList.toggle('dark', isNight)
  },
  { immediate: true },
)
app.mount('#app')

window.addEventListener('vite:preloadError', event => {
  event.preventDefault()
})
