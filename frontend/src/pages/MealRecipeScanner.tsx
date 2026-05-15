import React, { useState, useRef } from 'react'
import { Card, Button, Input, Badge } from '@components/common'
import { apiClient } from '@services/api_client'

interface ParsedReceipt {
  store: {
    chain: string
    name: string
    city: string
    postal_code: string
    confidence: number
  }
  transaction_date: string | null
  transaction_time: string | null
  items: Array<{
    name: string
    normalized_name: string
    quantity: number
    unit: string | null
    is_weighted: boolean
    weight_info: any
    total_price: number
    is_pfand: boolean
    confidence: number
  }>
  total: number
  subtotal: number | null
  discounts: Array<{ label: string; amount: number; is_pfand: boolean }>
  total_discounts: number
  payment: { method: string; amount_given: number | null } | null
}

type ScanMode = 'local' | 'anthropic' | 'openai'

const MealRecipeScanner: React.FC = () => {
  const [mode, setMode] = useState<ScanMode>('local')
  const [apiKey, setApiKey] = useState('')
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [rawText, setRawText] = useState('')
  const [parsedReceipt, setParsedReceipt] = useState<ParsedReceipt | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<'upload' | 'text'>('upload')

  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      const reader = new FileReader()
      reader.onload = (e) => {
        setImagePreview(e.target?.result as string)
        setParsedReceipt(null)
        setError(null)
      }
      reader.readAsDataURL(file)
    }
  }

  const handleScan = async () => {
    setLoading(true)
    setError(null)
    setParsedReceipt(null)

    try {
      if (tab === 'upload' && fileInputRef.current?.files?.[0]) {
        const file = fileInputRef.current.files[0]
        const effectiveKey = mode !== 'local' ? (apiKey || localStorage.getItem('receipt_api_key') || undefined) : undefined

        const response = await apiClient.parseReceiptFile(file, mode, effectiveKey)
        if (response.data.success) {
          setParsedReceipt(response.data.receipt)
        } else {
          setError(response.data.error || 'Failed to parse receipt')
        }
      } else if (tab === 'text' && rawText.trim()) {
        const effectiveKey = mode !== 'local' ? (apiKey || localStorage.getItem('receipt_api_key') || undefined) : undefined
        const response = await apiClient.parseReceiptText(rawText, mode, effectiveKey)
        if (response.data.success) {
          setParsedReceipt(response.data.receipt)
        } else {
          setError(response.data.error || 'Failed to parse receipt')
        }
      } else {
        setError('Please provide an image or text')
      }
    } catch (err: any) {
      setError(err.message || 'Failed to process receipt')
    } finally {
      setLoading(false)
    }
  }

  const handleClear = () => {
    setImagePreview(null)
    setRawText('')
    setParsedReceipt(null)
    setError(null)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const handleAddToExpenses = async (item: any) => {
    // TODO: Call expenses API to add this item
    console.log('Add to expenses:', item)
    alert(`Added "${item.name}" to expenses: €${item.total_price}`)
  }

  const handleAddAllToShopping = async () => {
    // TODO: Call shopping list API
    console.log('Add all to shopping list:', parsedReceipt?.items)
    alert(`Added ${parsedReceipt?.items.length} items to shopping list`)
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold">Receipt Scanner</h1>
        <p className="text-dark-text/60">Scan receipts and automatically extract items</p>
      </div>

      {/* Mode Selection */}
      <Card>
        <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center">
          <div className="flex-1">
            <label className="text-sm font-medium mb-2 block">Parsing Mode</label>
            <div className="flex gap-2">
              <button
                onClick={() => setMode('local')}
                className={`px-4 py-2 rounded-lg text-sm font-medium transition ${
                  mode === 'local'
                    ? 'bg-primary text-white'
                    : 'bg-dark-bg text-dark-text hover:bg-dark-border'
                }`}
              >
                🆓 Local
              </button>
              <button
                onClick={() => setMode('anthropic')}
                className={`px-4 py-2 rounded-lg text-sm font-medium transition ${
                  mode === 'anthropic'
                    ? 'bg-primary text-white'
                    : 'bg-dark-bg text-dark-text hover:bg-dark-border'
                }`}
              >
                🤖 Anthropic
              </button>
              <button
                onClick={() => setMode('openai')}
                className={`px-4 py-2 rounded-lg text-sm font-medium transition ${
                  mode === 'openai'
                    ? 'bg-primary text-white'
                    : 'bg-dark-bg text-dark-text hover:bg-dark-border'
                }`}
              >
                🤖 OpenAI
              </button>
            </div>
          </div>

          {mode !== 'local' && (
            <div className="flex-1 w-full sm:w-auto">
              <Input
                label="API Key (optional - saves to localStorage)"
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder={mode === 'anthropic' ? 'sk-ant-...' : 'sk-...'}
              />
              {apiKey && (
                <button
                  onClick={() => localStorage.setItem('receipt_api_key', apiKey)}
                  className="text-xs text-primary hover:underline -mt-3 block"
                >
                  Save key
                </button>
              )}
            </div>
          )}
        </div>
      </Card>

      {/* Tab Selection */}
      <div className="flex gap-2 border-b border-dark-border pb-2">
        <button
          onClick={() => setTab('upload')}
          className={`px-4 py-2 text-sm font-medium ${
            tab === 'upload' ? 'text-primary border-b-2 border-primary' : 'text-dark-text/60'
          }`}
        >
          📷 Upload Image
        </button>
        <button
          onClick={() => setTab('text')}
          className={`px-4 py-2 text-sm font-medium ${
            tab === 'text' ? 'text-primary border-b-2 border-primary' : 'text-dark-text/60'
          }`}
        >
          📝 Paste Text
        </button>
      </div>

      {/* Input Area */}
      {tab === 'upload' ? (
        <Card>
          <div className="space-y-4">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handleFileSelect}
              className="hidden"
              id="receipt-upload"
            />

            {!imagePreview ? (
              <label
                htmlFor="receipt-upload"
                className="border-2 border-dashed border-dark-border rounded-lg p-8 flex flex-col items-center cursor-pointer hover:border-primary transition"
              >
                <div className="text-4xl mb-2">📸</div>
                <p className="text-dark-text font-medium">Click to upload receipt</p>
                <p className="text-dark-text/60 text-sm">Supports JPG, PNG, HEIC</p>
              </label>
            ) : (
              <div className="relative">
                <img
                  src={imagePreview}
                  alt="Receipt preview"
                  className="max-h-64 mx-auto rounded-lg"
                />
                <button
                  onClick={handleClear}
                  className="absolute top-2 right-2 bg-dark-bg/80 text-white p-2 rounded-full hover:bg-status-error transition"
                >
                  ✕
                </button>
              </div>
            )}
          </div>
        </Card>
      ) : (
        <Card>
          <textarea
            value={rawText}
            onChange={(e) => setRawText(e.target.value)}
            placeholder="Paste receipt text here...&#10;&#10;Example:&#10;ALDI SÜD&#10;Milch 3,99&#10;Brot 2,49&#10;Gesamt 6,48"
            className="w-full h-48 bg-dark-bg border border-dark-border rounded-lg p-4 text-dark-text placeholder-dark-text/30 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary font-mono text-sm"
          />
        </Card>
      )}

      {/* Scan Button */}
      <div className="flex gap-4">
        <Button
          variant="primary"
          size="lg"
          onClick={handleScan}
          disabled={loading || (tab === 'upload' && !imagePreview) || (tab === 'text' && !rawText.trim())}
          loading={loading}
        >
          {loading ? 'Scanning...' : `Scan with ${mode === 'local' ? 'Local Parser' : mode === 'anthropic' ? 'Claude' : 'GPT'}`}
        </Button>
        {(imagePreview || rawText) && (
          <Button variant="ghost" onClick={handleClear}>
            Clear
          </Button>
        )}
      </div>

      {/* Error */}
      {error && (
        <Card className="border-status-error">
          <div className="text-status-error">
            <strong>Error:</strong> {error}
          </div>
        </Card>
      )}

      {/* Results */}
      {parsedReceipt && (
        <div className="space-y-4">
          {/* Summary */}
          <Card>
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-xl font-bold">{parsedReceipt.store.name}</h3>
                <p className="text-dark-text/60 text-sm">
                  {parsedReceipt.store.city && `${parsedReceipt.store.city} `}
                  {parsedReceipt.store.postal_code}
                </p>
              </div>
              <div className="text-right">
                <div className="text-3xl font-bold text-primary">
                  €{parsedReceipt.total.toFixed(2)}
                </div>
                <p className="text-dark-text/60 text-sm">
                  {parsedReceipt.transaction_date?.split('T')[0]}
                </p>
              </div>
            </div>

            {parsedReceipt.payment && (
              <div className="flex gap-4 text-sm text-dark-text/60">
                <span>Payment: {parsedReceipt.payment.method}</span>
                {parsedReceipt.payment.amount_given && (
                  <span>Given: €{parsedReceipt.payment.amount_given.toFixed(2)}</span>
                )}
              </div>
            )}
          </Card>

          {/* Items */}
          <Card>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold">Items ({parsedReceipt.items.length})</h3>
              <Button size="sm" variant="secondary" onClick={handleAddAllToShopping}>
                + Add All to Shopping
              </Button>
            </div>

            <div className="space-y-2">
              {parsedReceipt.items.map((item, idx) => (
                <div
                  key={idx}
                  className="flex items-center justify-between p-3 bg-dark-bg rounded-lg hover:bg-dark-border/50 transition"
                >
                  <div className="flex-1">
                    <div className="font-medium">{item.name}</div>
                    {item.is_weighted && item.weight_info && (
                      <div className="text-xs text-dark-text/60">
                        {item.weight_info.quantity} {item.weight_info.unit} × €{item.weight_info.price_per_unit?.toFixed(2)}/kg
                      </div>
                    )}
                    {item.is_pfand && (
                      <Badge variant="warning">Pfand</Badge>
                    )}
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-bold">€{item.total_price.toFixed(2)}</span>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => handleAddToExpenses(item)}
                    >
                      +
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </Card>

          {/* Discounts */}
          {parsedReceipt.discounts.length > 0 && (
            <Card>
              <h3 className="text-lg font-semibold mb-3">Discounts & Extras</h3>
              <div className="space-y-2">
                {parsedReceipt.discounts.map((d, idx) => (
                  <div key={idx} className="flex justify-between">
                    <span className={d.is_pfand ? 'text-status-warning' : ''}>
                      {d.label} {d.is_pfand && '💰'}
                    </span>
                    <span className="font-medium">
                      {d.is_pfand ? '+' : '-'}€{d.amount.toFixed(2)}
                    </span>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>
      )}
    </div>
  )
}

export default MealRecipeScanner