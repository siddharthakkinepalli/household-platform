import React, { useState } from 'react'
import { Card, Button, Input, Loader, EmptyState } from '@components/common'
import { useImportCSV } from '@hooks'
import { formatFileSize } from '@utils/formatting'

const ExpenseImport: React.FC = () => {
  const [file, setFile] = useState<File | null>(null)
  const [bank, setBank] = useState('')
  const [preview, setPreview] = useState<string[][]>([])
  const importMutation = useImportCSV()

  const banks = [
    { value: 'commerzbank', label: 'Commerzbank' },
    { value: 'ing', label: 'ING' },
    { value: 'n26', label: 'N26' },
    { value: 'revolut', label: 'Revolut' },
  ]

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0]
    if (!selectedFile) return

    setFile(selectedFile)
    const text = await selectedFile.text()
    const lines = text.split('\n').slice(0, 5)
    const rows = lines.map(line => line.split(','))
    setPreview(rows)
  }

  const handleImport = async () => {
    if (!file) return
    try {
      await importMutation.mutateAsync({ file, bank })
      setFile(null)
      setPreview([])
      alert('Import successful!')
    } catch (err) {
      console.error('Import error:', err)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Import Expenses</h1>
        <p className="text-dark-text/60">Import transactions from bank statements</p>
      </div>

      <Card>
        <h3 className="text-lg font-semibold mb-4">CSV Import</h3>
        <div className="space-y-4">
          {/* Bank Selection */}
          <div>
            <label className="block text-sm font-medium mb-2">Bank</label>
            <select
              value={bank}
              onChange={(e) => setBank(e.target.value)}
              className="w-full px-4 py-2 bg-dark-bg border border-dark-border rounded-lg focus:border-primary focus:ring-1 focus:ring-primary"
            >
              <option value="">Auto-detect</option>
              {banks.map(b => (
                <option key={b.value} value={b.value}>{b.label}</option>
              ))}
            </select>
          </div>

          {/* File Input */}
          <div
            className="border-2 border-dashed border-dark-border rounded-lg p-8 text-center hover:border-primary transition-colors cursor-pointer"
            onClick={() => document.getElementById('file-input')?.click()}
          >
            <input
              id="file-input"
              type="file"
              accept=".csv,.txt"
              onChange={handleFileSelect}
              className="hidden"
            />
            <div className="text-4xl mb-2">📄</div>
            <p className="font-medium mb-1">Drag & drop CSV or click to select</p>
            <p className="text-sm text-dark-text/60">
              Supports Commerzbank, ING, N26, Revolut formats
            </p>
            {file && (
              <div className="mt-4 p-3 bg-dark-bg rounded border border-primary/50">
                <p className="text-sm">{file.name} ({formatFileSize(file.size)})</p>
              </div>
            )}
          </div>

          {/* Preview */}
          {preview.length > 0 && (
            <div>
              <h4 className="font-semibold mb-2">Preview</h4>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <tbody>
                    {preview.map((row, idx) => (
                      <tr key={idx} className="border-b border-dark-border/50">
                        {row.map((cell, cidx) => (
                          <td key={cidx} className="py-2 px-3">{cell}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Actions */}
          {file && (
            <div className="flex gap-2">
              <Button
                variant="primary"
                onClick={handleImport}
                loading={importMutation.isPending}
              >
                Import Transactions
              </Button>
              <Button
                variant="secondary"
                onClick={() => {
                  setFile(null)
                  setPreview([])
                }}
              >
                Clear
              </Button>
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}

export default ExpenseImport
