import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import './styles/tailwind.css'
import { useTheme } from './composables/useTheme'
import { useNetworkStore } from './stores/network'

useTheme()

const app = createApp(App)
app.use(createPinia())

const network = useNetworkStore()
network.init()

app.use(router)
app.mount('#app')
