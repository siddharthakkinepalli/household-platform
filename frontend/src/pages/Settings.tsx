import React, { useState } from 'react'
import { Card, Button, Input, Select } from '@components/common'

const Settings: React.FC = () => {
  const [activeTab, setActiveTab] = useState('household')
  const [settings, setSettings] = useState({
    householdName: 'My Household',
    currency: 'EUR',
    language: 'en',
    monthlyBudget: 3000,
  })

  const tabs = [
    { id: 'household', label: 'Household', icon: '🏠' },
    { id: 'members', label: 'Members', icon: '👥' },
    { id: 'budget', label: 'Budget', icon: '💰' },
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

      {/* Tab Content */}
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
