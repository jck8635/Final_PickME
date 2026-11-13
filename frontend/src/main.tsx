import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App'
import { AuthProvider, FlashProvider } from './lib/app-context'
import './styles/index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <FlashProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </FlashProvider>
    </BrowserRouter>
  </StrictMode>,
)
