import { useEffect, useMemo, useState } from 'react'
import { Routes, Route, useNavigate, Navigate, useLocation } from 'react-router-dom'
import {
  acceptInvitation,
  login,
  logout as logoutRequest,
  requestPasswordReset,
  setAuthToken,
  validateInvitation,
} from '../api/authApi'
import type { User } from '../types/auth.types'
import LoginScreen from '../features/auth/LoginScreen'
import ForgotScreen from '../features/auth/ForgotScreen'
import ForgotSentScreen from '../features/auth/ForgotSentScreen'
import InviteScreen from '../features/auth/InviteScreen'
import NoAccountScreen from '../features/auth/NoAccountScreen'
import DashboardScreen from '../features/dashboard/DashboardScreen'
import SubmissionScreen from '../features/submission/SubmissionScreen'
import SessionModal from '../components/modals/SessionModal'

const LOCKOUT_LIMIT = 5
const LOCKOUT_SECONDS = 15 * 60

function App() {
  const navigate = useNavigate()
  const location = useLocation()

  const [loginLoading, setLoginLoading] = useState(false)
  const [forgotLoading, setForgotLoading] = useState(false)
  const [inviteLoading, setInviteLoading] = useState(false)
  
  const [loginEmail, setLoginEmail] = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [showLoginPassword, setShowLoginPassword] = useState(false)
  const [loginError, setLoginError] = useState('')
  const [attempts, setAttempts] = useState(0)
  const [lockRemaining, setLockRemaining] = useState(0)
  const [lockTimerId, setLockTimerId] = useState<number | null>(null)

  const [modalEmail, setModalEmail] = useState('')
  const [modalPassword, setModalPassword] = useState('')
  const [modalError, setModalError] = useState(false)
  const [showModalPassword, setShowModalPassword] = useState(false)

  const [forgotEmail, setForgotEmail] = useState('')
  const [forgotSentEmail, setForgotSentEmail] = useState('')

  const [inviteToken, setInviteToken] = useState<string | null>(null)
  const [inviteState, setInviteState] = useState<'form' | 'success' | 'expired' | 'already'>('form')
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState('')
  const [inviteInstitution, setInviteInstitution] = useState('')
  const [invitePassword, setInvitePassword] = useState('')
  const [inviteConfirmPassword, setInviteConfirmPassword] = useState('')
  const [showInvitePassword, setShowInvitePassword] = useState(false)
  const [showInviteConfirmPassword, setShowInviteConfirmPassword] = useState(false)
  const [inviteCountdown, setInviteCountdown] = useState('')

  const [showDropdown, setShowDropdown] = useState(false)
  const [currentUser, setCurrentUser] = useState<User | null>(null)
  const [authReady, setAuthReady] = useState(false)
  const [showSessionModal, setShowSessionModal] = useState(false)
  
  const [bannerRemaining, setBannerRemaining] = useState(0)
  const [bannerTimerId, setBannerTimerId] = useState<number | null>(null)

  const inviteRules = useMemo(() => {
    const length = invitePassword.length >= 8
    const upper = /[A-Z]/.test(invitePassword)
    const number = /[0-9]/.test(invitePassword)
    const symbol = /[^A-Za-z0-9]/.test(invitePassword)
    const match = inviteConfirmPassword.length > 0 && invitePassword === inviteConfirmPassword
    return { length, upper, number, symbol, match }
  }, [invitePassword, inviteConfirmPassword])

  useEffect(() => {
    const savedToken = localStorage.getItem('dasigconnect_token')
    const savedUser = localStorage.getItem('dasigconnect_user')
    if (savedToken && savedUser) {
      setAuthToken(savedToken)
      try {
        setCurrentUser(JSON.parse(savedUser))
      } catch {
        localStorage.removeItem('dasigconnect_token')
        localStorage.removeItem('dasigconnect_user')
      }
    }
    setAuthReady(true)
  }, [])

  useEffect(() => {
    if (location.pathname !== '/invite') return
    const params = new URLSearchParams(location.search)
    const token = params.get('token') || params.get('inviteToken')
    if (token) {
      setInviteToken(token)
      void validateInviteToken(token)
    } else {
      setInviteState('expired')
    }
  }, [location.pathname, location.search])

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Enter') return
      
      if (location.pathname === '/' || location.pathname === '/login') {
        void handleLogin()
      } else if (location.pathname === '/forgot-password') {
        void handleForgotSubmit()
      } else if (location.pathname === '/dashboard' && showSessionModal) {
        void handleModalLogin()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [location.pathname, showSessionModal, loginEmail, loginPassword, modalEmail, modalPassword])

  useEffect(() => {
    return () => {
      if (lockTimerId) window.clearInterval(lockTimerId)
      if (bannerTimerId) window.clearInterval(bannerTimerId)
    }
  }, [lockTimerId, bannerTimerId])

  const bannerTime = formatTimer(bannerRemaining)

  function triggerLockout() {
    setLockRemaining(LOCKOUT_SECONDS)
    const id = window.setInterval(() => {
      setLockRemaining((prev) => {
        if (prev <= 1) {
          window.clearInterval(id)
          setLockTimerId(null)
          setAttempts(0)
          return 0
        }
        return prev - 1
      })
    }, 1000)
    setLockTimerId(id)
  }

  function resetLoginState() {
    setLoginPassword('')
    setLoginError('')
    setAttempts(0)
    setLockRemaining(0)
    if (lockTimerId) {
      window.clearInterval(lockTimerId)
      setLockTimerId(null)
    }
  }

  async function handleLogin() {
    if (lockRemaining > 0) return
    setLoginLoading(true)
    const email = loginEmail.trim().toLowerCase()
    try {
      const response = await login(email, loginPassword)
      const apiUser = response.data
      const role = mapApiRole(apiUser.role)
      const nameFromEmail = email.split('@')[0] || 'User'
      const user: User = {
        email,
        pw: '',
        role,
        name: nameFromEmail
          .split('.')
          .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
          .join(' '),
        inst: apiUser.institutionId || 'Institution',
        initials: initialsFromEmail(email),
      }
      setAuthToken(apiUser.accessToken)
      localStorage.setItem('dasigconnect_token', apiUser.accessToken)
      localStorage.setItem('dasigconnect_user', JSON.stringify(user))
      setCurrentUser(user)
      navigate('/dashboard')
      resetLoginState()
    } catch (err: any) {
      const nextAttempts = attempts + 1
      setAttempts(nextAttempts)
      if (nextAttempts >= LOCKOUT_LIMIT) {
        triggerLockout()
      } else {
        setLoginError(
          err.response?.data?.message ||
            `Invalid credentials. ${LOCKOUT_LIMIT - nextAttempts} attempts remaining before lockout.`
        )
      }
    } finally {
      setLoginLoading(false)
    }
  }

  async function handleForgotSubmit() {
    const email = forgotEmail.trim() || 'yourname@institution.edu.ph'
    setForgotLoading(true)
    try {
      await requestPasswordReset(email)
    } catch {
      // Intentionally silent to avoid email enumeration.
    } finally {
      setForgotLoading(false)
      setForgotSentEmail(email)
      navigate('/forgot-password-sent')
    }
  }

  async function handleInviteActivate() {
    if (!inviteToken) return
    setInviteLoading(true)
    try {
      const response = await acceptInvitation(inviteToken, invitePassword)
      setAuthToken(response.data.accessToken)
      localStorage.setItem('dasigconnect_token', response.data.accessToken)
      setInviteState('success')
    } catch {
      setInviteState('expired')
    } finally {
      setInviteLoading(false)
    }
  }

  async function handleLogout() {
    try {
      await logoutRequest()
    } catch {
      // Best-effort logout.
    }
    localStorage.removeItem('dasigconnect_token')
    localStorage.removeItem('dasigconnect_user')
    setAuthToken(null)
    setCurrentUser(null)
    setShowDropdown(false)
    setShowSessionModal(false)
    stopBanner()
    resetLoginState()
    navigate('/login')
  }

  async function handleModalLogin() {
    const email = modalEmail.trim().toLowerCase()
    try {
      const response = await login(email, modalPassword)
      setAuthToken(response.data.accessToken)
      localStorage.setItem('dasigconnect_token', response.data.accessToken)
      setShowSessionModal(false)
      setModalError(false)
    } catch {
      setModalError(true)
    }
  }

  function stopBanner() {
    if (bannerTimerId) window.clearInterval(bannerTimerId)
    setBannerRemaining(0)
    setBannerTimerId(null)
  }

  function handleStayLoggedIn() {
    stopBanner()
  }

  async function validateInviteToken(token: string) {
    try {
      const response = await validateInvitation(token)
      const data = response.data
      setInviteEmail(data.recipientEmail)
      setInviteRole(formatRoleLabel(data.assignedRole))
      setInviteInstitution(data.institutionName)
      setInviteCountdown(`Invitation expires ${formatExpiry(data.expiresAt)}`)
    } catch {
      setInviteState('expired')
    }
  }

  if (!authReady) {
    return <div className="screen active" />
  }

  return (
    <>
      <Routes>
        <Route path="/" element={
          currentUser ? <Navigate to="/dashboard" replace /> : <Navigate to="/login" replace />
        } />
        
        <Route path="/login" element={
          <LoginScreen
            active={true}
            email={loginEmail}
            password={loginPassword}
            showPassword={showLoginPassword}
            loginError={loginError}
            attempts={attempts}
            lockRemaining={lockRemaining}
            onEmailChange={setLoginEmail}
            onPasswordChange={setLoginPassword}
            onTogglePassword={() => setShowLoginPassword(!showLoginPassword)}
            onLogin={() => void handleLogin()}
            onForgot={() => navigate('/forgot-password')}
            onNoAccount={() => navigate('/no-account')}
            onRequestReset={() => navigate('/forgot-password')}
            loading={loginLoading}
          />
        } />

        <Route path="/forgot-password" element={
          <ForgotScreen
            active={true}
            email={forgotEmail}
            onEmailChange={setForgotEmail}
            onSubmit={() => void handleForgotSubmit()}
            onBack={() => navigate('/login')}
            loading={forgotLoading}
          />
        } />

        <Route path="/forgot-password-sent" element={
          <ForgotSentScreen
            active={true}
            email={forgotSentEmail}
            onBack={() => navigate('/login')}
          />
        } />

        <Route path="/invite" element={
          <InviteScreen
            active={true}
            state={inviteState}
            email={inviteEmail}
            roleLabel={inviteRole}
            institution={inviteInstitution}
            password={invitePassword}
            confirmPassword={inviteConfirmPassword}
            rules={inviteRules}
            inviteCountdown={inviteCountdown}
            onPasswordChange={setInvitePassword}
            onConfirmPasswordChange={setInviteConfirmPassword}
            onTogglePassword={() => setShowInvitePassword(!showInvitePassword)}
            onToggleConfirmPassword={() => setShowInviteConfirmPassword(!showInviteConfirmPassword)}
            onActivate={() => void handleInviteActivate()}
            onBackToLogin={() => navigate('/login')}
            showPassword={showInvitePassword}
            showConfirmPassword={showInviteConfirmPassword}
            loading={inviteLoading}
          />
        } />

        <Route path="/no-account" element={
          <NoAccountScreen active={true} onBack={() => navigate('/login')} />
        } />

        <Route path="/dashboard" element={
          currentUser ? (
            <DashboardScreen
              active={true}
              user={currentUser}
              showBanner={bannerRemaining > 0}
              bannerTime={bannerTime}
              showDropdown={showDropdown}
              onToggleDropdown={() => setShowDropdown(!showDropdown)}
              onDismissBanner={stopBanner}
              onStayLoggedIn={handleStayLoggedIn}
              onLogout={() => void handleLogout()}
            />
          ) : (
            <Navigate to="/login" replace />
          )
        } />

        <Route path="/submissions/new" element={
          currentUser ? <SubmissionScreen user={currentUser} /> : <Navigate to="/login" replace />
        } />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      <SessionModal
        open={showSessionModal}
        email={modalEmail}
        password={modalPassword}
        error={modalError}
        onEmailChange={setModalEmail}
        onPasswordChange={setModalPassword}
        onTogglePassword={() => setShowModalPassword(!showModalPassword)}
        onSubmit={() => void handleModalLogin()}
        showPassword={showModalPassword}
      />
    </>
  )
}

function formatTimer(seconds: number) {
  const safeSeconds = Math.max(seconds, 0)
  const minutes = Math.floor(safeSeconds / 60)
  const remaining = safeSeconds % 60
  return `${minutes.toString().padStart(2, '0')}:${remaining
    .toString()
    .padStart(2, '0')}`
}

function mapApiRole(role: string): User['role'] {
  const normalized = role.toLowerCase()
  if (normalized.includes('admin')) return 'admin'
  if (normalized.includes('validator')) return 'validator'
  return 'contributor'
}

function initialsFromEmail(email: string) {
  const name = email.split('@')[0] || 'U'
  const parts = name.split('.')
  if (parts.length >= 2) {
    return `${parts[0].charAt(0)}${parts[1].charAt(0)}`.toUpperCase()
  }
  return name.substring(0, 2).toUpperCase()
}

function formatRoleLabel(role: string) {
  if (!role) return 'Contributor'
  const normalized = role.toLowerCase()
  return normalized.charAt(0).toUpperCase() + normalized.slice(1)
}

function formatExpiry(expiresAt: string) {
  const date = new Date(expiresAt)
  if (Number.isNaN(date.getTime())) return 'soon'
  const diff = date.getTime() - Date.now()
  if (diff <= 0) return 'soon'
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  return `in ${hours}h ${minutes}m`
}

export default App
