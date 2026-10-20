import API from '@api'
import { useBookStore } from '@/store'

export const useAppTheme = () => {
  const store = useBookStore()
  const isNight = computed(() => store.isNight)
  const ready = ref(false)
  const isSaving = ref(false)

  const markReady = () => {
    ready.value = true
  }

  const toggleTheme = async () => {
    if (!ready.value || isSaving.value) return
    const previousTheme = store.config.theme
    store.config.theme = previousTheme === 6 ? 0 : 6
    isSaving.value = true
    try {
      const { data } = await API.saveReadConfig({ ...store.config })
      if (!data.isSuccess) throw new Error(data.errorMsg || '主题保存失败')
    } catch (error) {
      store.config.theme = previousTheme
      ElMessage.error(
        error instanceof Error ? error.message : '主题保存失败，请检查后端连接',
      )
    } finally {
      isSaving.value = false
    }
  }

  return { isNight, ready, isSaving, markReady, toggleTheme }
}
