import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import { definePreset } from '@primevue/themes'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import Tooltip from 'primevue/tooltip'
import 'primeicons/primeicons.css'
import 'diff2html/bundles/css/diff2html.min.css'
import './style.css'
import App from './App.vue'

const app = createApp(App)

const Noir = definePreset(Aura, {
    semantic: {
        primary: {
            50: '{slate.50}',
            100: '{slate.100}',
            200: '{slate.200}',
            300: '{slate.300}',
            400: '{slate.400}',
            500: '{slate.900}',
            600: '{slate.800}',
            700: '{slate.700}',
            800: '{slate.600}',
            900: '{slate.500}',
            950: '{slate.400}'
        },
        colorScheme: {
            light: {
                primary: {
                    color: '{slate.900}',
                    inverseColor: '#ffffff',
                    hoverColor: '{slate.800}',
                    activeColor: '{slate.700}'
                },
                highlight: {
                    background: '{slate.900}',
                    focusBackground: '{slate.700}',
                    color: '#ffffff',
                    focusColor: '#ffffff'
                }
            },
            dark: {
                primary: {
                    color: '{slate.50}',
                    inverseColor: '{slate.900}',
                    hoverColor: '{slate.100}',
                    activeColor: '{slate.200}'
                },
                highlight: {
                    background: '{slate.50}',
                    focusBackground: '{slate.100}',
                    color: '{slate.900}',
                    focusColor: '{slate.900}'
                }
            }
        }
    }
});

app.use(PrimeVue, {
    theme: {
        preset: Noir,
        options: {
            darkModeSelector: '.my-app-dark',
            cssLayer: false
        }
    },
    ripple: true
})
app.use(ToastService)
app.use(ConfirmationService)
app.directive('tooltip', Tooltip)

app.mount('#app')
