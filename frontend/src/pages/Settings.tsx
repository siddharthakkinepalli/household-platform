import React, { useState, useEffect } from 'react'
import { Card, Button, Input, Select } from '@components/common'
import apiClient from '@services/api_client'

const HOUSEHOLD_ID = 1

interface BankConnection {
  id: number
  bank_name: string
  institution_id: string
  account_ids: string[]
  last_sync_at: string | null
  created_at: string
  is_active: boolean
}

interface SyncResult {
  imported: number
  skipped: number
}

const Settings: React.FC = () => {
  const [activeTab, setActiveTab] = useState('household')
  const [settings, setSettings] = useState({
    householdName: 'My Household',
    currency: 'EUR',
    language: 'en',
    monthlyBudget: 3000,
  })

  // Bank connection state
  const [connections, setConnections] = useState<BankConnection[]>([])
  const [linkLoading, setLinkLoading] = useState(false)
  const [syncingId, setSyncingId] = useState<number | null>(null)
  const [syncResult, setSyncResult] = useState<SyncResult | null>(null)
  const [bankError, setBankError] = useState<string | null>(null)

  useEffect(() => {
    if (activeTab === 'banks') {
      fetchConnections()
    }
  }, [activeTab])

  const fetchConnections = async () => {
    try {
      const res = await apiClient.getPlaidConnections(HOUSEHOLD_ID)
      setConnections(res.data.connections || [])
    } catch {
      // silently fail — connections list will just be empty
    }
  }

  const loadPlaidScript = (): Promise<void> =>
    new Promise((resolve, reject) => {
      if ((window as any).Plaid) { resolve(); return }
      const s = document.createElement('script')
      s.src = 'https://cdn.plaid.com/link/v2/stable/link-initialize.js'
      s.onload = () => resolve()
      s.onerror = () => reject(new Error('Failed to load Plaid Link script'))
      document.head.appendChild(s)
    })

  const handleConnectBank = async () => {
    setLinkLoading(true)
    setBankError(null)
    try {
      const tokenRes = await apiClient.createPlaidLinkToken(HOUSEHOLD_ID)
      const linkToken = tokenRes.data.link_token
      await loadPlaidScript()

      const handler = (window as any).Plaid.create({
        token: linkToken,
        onSuccess: async (public_token: string, metadata: any) => {
          try {
            await apiClient.exchangePlaidToken({
              household_id: HOUSEHOLD_ID,
              public_token,
              institution_name: metadata.institution?.name,
              institution_id: metadata.institution?.institution_id,
            })
            await fetchConnections()
          } catch (e: any) {
            setBankError(e.message || 'Failed to save bank connection')
          } finally {
            setLinkLoading(false)
          }
        },
        onExit: (err: any) => {
          if (err) setBankError(err.display_message || err.error_message || null)
          setLinkLoading(false)
        },
      })
      handler.open()
    } catch (e: any) {
      setBankError(e.message || 'Failed to start bank connection')
      setLinkLoading(false)
    }
  }

  const handleSync = async (connectionId: number) => {
    setSyncingId(connectionId)
    setSyncResult(null)
    setBankError(null)
    try {
      const res = await apiClient.syncPlaidTransactions(HOUSEHOLD_ID, connectionId)
      setSyncResult(res.data)
      await fetchConnections()
    } catch (e: any) {
      setBankError(e.message || 'Sync failed')
    } finally {
      setSyncingId(null)
    }
  }

  const handleDisconnect = async (connectionId: number) => {
    if (!window.confirm('Disconnect this bank? Existing transactions will remain.')) return
    setBankError(null)
    try {
      await apiClient.removePlaidConnection(connectionId, HOUSEHOLD_ID)
      await fetchConnections()
    } catch (e: any) {
      setBankError(e.message || 'Failed to disconnect bank')
    }
  }

  const tabs = [
    { id: 'household', label: 'Household', icon: '🏠' },
    { id: 'members', label: 'Members', icon: '👥' },
    { id: 'budget', label: 'Budget', icon: '💰' },
    { id: 'banks', label: 'Banks', icon: '🏦' },
    { id: 'preferences', label: 'Preferences', icon: '⚙️' },
    { id: 'privacy', label: 'Privacy', icon: '🔒' },
    { id: 'notifications', label: 'Notifications', icon: '🔔' },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Settings</h1>
        <p className="text-dark-text/60">Manage your household and preferences</p>
      </div>

      {/* Tab Navigation */}
      <div className="flex gap-2 overflow-x-auto border-b border-dark-border">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-3 whitespace-nowrap font-medium transition-all ${
              activeTab === tab.id
                ? 'border-b-2 border-primary text-primary'
                : 'text-dark-text/60 hover:text-dark-text'
            }`}
          >
            {tab.icon} {tab.label}
          </button>
        ))}
      </div>

      {/* Household Tab */}
      {activeTab === 'household' && (
        <Card className="space-y-4">
          <h3 className="text-lg font-semibold">Household Profile</h3>
          <Input
            label="Household Name"
            value={settings.householdName}
            onChange={(e) => setSettings({ ...settings, householdName: e.target.value })}
          />
          <Select
            label="Currency"
            options={[
              { value: 'EUR', label: 'EUR (€)' },
              { value: 'USD', label: 'USD ($)' },
              { value: 'GBP', label: 'GBP (£)' },
            ]}
            value={settings.currency}
            onChange={(e) => setSettings({ ...settings, currency: e.target.value })}
          />
          <Button variant="primary">Save Changes</Button>
        </Card>
      )}

      {/* Members Tab */}
      {activeTab === 'members' && (
        <Card className="space-y-4">
          <h3 className="text-lg font-semibold">Family Members</h3>
          <div className="space-y-3">
            <div className="flex items-center justify-between p-3 bg-dark-bg rounded">
              <div>
                <p className="font-medium">You</p>
                <p className="text-sm text-dark-text/60">Admin</p>
              </div>
              <Button variant="ghost" size="sm">Settings</Button>
            </div>
          </div>
          <Button variant="primary">+ Invite Member</Button>
        </Card>
      )}

      {/* Budget Tab */}
      {activeTab === 'budget' && (
        <Card className="space-y-4">
          <h3 className="text-lg font-semibold">Budget Configuration</h3>
          <Input
            label="Monthly Budget"
            type="number"
            value={settings.monthlyBudget}
            onChange={(e) => setSettings({ ...settings, monthlyBudget: parseFloat(e.target.value) })}
          />
          <div>
            <h4 className="font-medium mb-3">Budget by Category</h4>
            <div className="space-y-2">
              {['Groceries', 'Entertainment', 'Transport'].map((cat) => (
                <Input
                  key={cat}
                  label={cat}
                  type="number"
                  placeholder="€ 0.00"
                />
              ))}
            </div>
          </div>
          <Button variant="primary">Save Budget</Button>
        </Card>
      )}

      {/* Banks Tab */}
      {activeTab === 'banks' && (
        <div className="space-y-4">
          <Card className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-lg font-semibold">Connected Banks</h3>
                <p className="text-sm text-dark-text/60 mt-1">
                  Auto-import transactions from N26, Commerzbank, and 12,000+ banks via Plaid.
                  CSV import still works alongside bank sync.
                </p>
              </div>
              <Button
                variant="primary"
                onClick={handleConnectBank}
                disabled={linkLoading}
              >
                {linkLoading ? 'Connecting…' : '+ Connect Bank'}
              </Button>
            </div>

            {bankError && (
              <div className="p-3 bg-status-error/10 border border-status-error/30 rounded text-sm text-status-error">
                {bankError}
              </div>
            )}

            {connections.length === 0 ? (
              <div className="text-center py-10 text-dark-text/40">
                <p className="text-3xl mb-2">🏦</p>
                <p className="font-medium">No banks connected</p>
                <p className="text-sm mt-1">Click "Connect Bank" to link N26 or Commerzbank.</p>
              </div>
            ) : (
              <div className="space-y-3">
                {connections.map((conn) => (
                  <div
                    key={conn.id}
                    className="flex items-center justify-between p-4 bg-dark-bg rounded-lg"
                  >
                    <div className="flex items-center gap-3">
                      <span className="text-2xl">🏦</span>
                      <div>
                        <p className="font-medium">{conn.bank_name}</p>
                        <p className="text-sm text-dark-text/60">
                          {conn.last_sync_at
                            ? `Last synced ${new Date(conn.last_sync_at).toLocaleDateString('en-DE', {
                                day: '2-digit', month: 'short', year: 'numeric',
                              })}`
                            : 'Never synced'}
                        </p>
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => handleSync(conn.id)}
                        disabled={syncingId === conn.id}
                      >
                        {syncingId === conn.id ? '⟳ Syncing…' : '⟳ Sync'}
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-status-error hover:text-status-error"
                        onClick={() => handleDisconnect(conn.id)}
                      >
                        Disconnect
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {syncResult && (
            <Card>
              <p className="text-sm text-dark-text/60">
                ✓ Sync complete — imported{' '}
                <span className="text-status-success font-medium">{syncResult.imported}</span>{' '}
                transactions, skipped{' '}
                <span className="font-medium">{syncResult.skipped}</span> duplicates.
              </p>
            </Card>
          )}

          <Card className="space-y-2">
            <h4 className="font-medium text-sm text-dark-text/60 uppercase tracking-wide">About Bank Sync</h4>
            <ul className="text-sm text-dark-text/60 space-y-1">
              <li>• Transactions are fetched for the last 90 days on each sync</li>
              <li>• Duplicate detection prevents re-importing the same transaction</li>
              <li>• If you also import CSV statements, overlapping transactions may appear twice — use "Exclude" on the Expenses page to hide them</li>
              <li>• Currently running in Plaid sandbox mode (test data only)</li>
            </ul>
          </Card>
        </div>
      )}

      {/* Preferences Tab */}
      {activeTab === 'preferences' && (
        <Card className="space-y-4">
          <h3 className="text-lg font-semibold">Preferences</h3>
          <Select
            label="Language"
            options={[
              { value: 'en', label: 'English' },
              { value: 'de', label: 'Deutsch' },
              { value: 'es', label: 'Español' },
            ]}
            value={settings.language}
            onChange={(e) => setSettings({ ...settings, language: e.target.value })}
          />
          <div className="flex items-center justify-between p-3 bg-dark-bg rounded">
            <span>Dark Mode</span>
            <input type="checkbox" defaultChecked className="w-5 h-5" />
          </div>
          <Button variant="primary">Save Preferences</Button>
        </Card>
      )}

      {/* Privacy Tab */}
      {activeTab === 'privacy' && (
        <Card className="space-y-4">
          <h3 className="text-lg font-semibold">Privacy & Data</h3>
          <div className="space-y-3">
            <Button variant="secondary">📥 Backup Data</Button>
            <Button variant="secondary">📤 Export as JSON</Button>
            <Button variant="secondary">📂 Restore from Backup</Button>
            <Button variant="secondary" className="border-status-error text-status-error">
              ⚠️ Delete All Data
            </Button>
          </div>
        </Card>
      )}

      {/* Notifications Tab */}
      {activeTab === 'notifications' && (
        <Card className="space-y-4">
          <h3 className="text-lg font-semibold">Notifications</h3>
          <div className="space-y-3">
            {[
              { label: 'Budget alerts', desc: 'When spending exceeds budget' },
              { label: 'Meal reminders', desc: 'For upcoming meal prep' },
              { label: 'Sync updates', desc: 'When data syncs with devices' },
            ].map((notif) => (
              <div key={notif.label} className="flex items-center justify-between p-3 bg-dark-bg rounded">
                <div>
                  <p className="font-medium">{notif.label}</p>
                  <p className="text-sm text-dark-text/60">{notif.desc}</p>
                </div>
                <input type="checkbox" defaultChecked className="w-5 h-5" />
              </div>
            ))}
          </div>
          <Button variant="primary">Save Notifications</Button>
        </Card>
      )}
    </div>
  )
}

export default Settings
