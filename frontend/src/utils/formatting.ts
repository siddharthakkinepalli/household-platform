import { format, formatDistanceToNow, parseISO } from 'date-fns'

// Currency formatting
export const formatCurrency = (amount: number, currency: string = 'EUR'): string => {
  return new Intl.NumberFormat('de-DE', {
    style: 'currency',
    currency: currency,
  }).format(amount)
}

// Number formatting
export const formatNumber = (num: number, decimals: number = 2): string => {
  return num.toFixed(decimals)
}

// Percentage formatting
export const formatPercent = (value: number, decimals: number = 1): string => {
  return `${value.toFixed(decimals)}%`
}

// Date formatting
export const formatDate = (date: string | Date, format_str: string = 'dd.MM.yyyy'): string => {
  const dateObj = typeof date === 'string' ? parseISO(date) : date
  return format(dateObj, format_str)
}

// Date & time formatting
export const formatDateTime = (date: string | Date, format_str: string = 'dd.MM.yyyy HH:mm'): string => {
  const dateObj = typeof date === 'string' ? parseISO(date) : date
  return format(dateObj, format_str)
}

// Relative time (e.g., "2 hours ago")
export const formatRelativeTime = (date: string | Date): string => {
  const dateObj = typeof date === 'string' ? parseISO(date) : date
  return formatDistanceToNow(dateObj, { addSuffix: true })
}

// Get week number
export const getWeekNumber = (date: Date = new Date()): number => {
  const firstDayOfYear = new Date(date.getFullYear(), 0, 1)
  const pastDaysOfYear = (date.getTime() - firstDayOfYear.getTime()) / 86400000
  return Math.ceil((pastDaysOfYear + firstDayOfYear.getDay() + 1) / 7)
}

// Get month name
export const getMonthName = (month: number): string => {
  const months = ['January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December']
  return months[month] || ''
}

// Get day name
export const getDayName = (day: number): string => {
  const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
  return days[day] || ''
}

// Truncate text
export const truncateText = (text: string, maxLength: number = 50): string => {
  return text.length > maxLength ? text.substring(0, maxLength) + '...' : text
}

// Capitalize text
export const capitalize = (text: string): string => {
  return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase()
}

// Category icon mapping
export const getCategoryIcon = (category: string): string => {
  const categoryIcons: { [key: string]: string } = {
    'Groceries': '🛒',
    'Restaurants': '🍽️',
    'Entertainment': '🎬',
    'Transport': '🚗',
    'Utilities': '💡',
    'Health': '🏥',
    'Shopping': '🛍️',
    'Travel': '✈️',
    'Insurance': '🛡️',
    'Salary': '💼',
    'Bonus': '🎁',
    'Investment': '📈',
    'Other': '📦',
  }
  return categoryIcons[category] || '💰'
}

// Category color mapping
export const getCategoryColor = (category: string): string => {
  const categoryColors: { [key: string]: string } = {
    'Groceries': 'bg-green-500/20 text-green-400',
    'Restaurants': 'bg-orange-500/20 text-orange-400',
    'Entertainment': 'bg-purple-500/20 text-purple-400',
    'Transport': 'bg-blue-500/20 text-blue-400',
    'Utilities': 'bg-yellow-500/20 text-yellow-400',
    'Health': 'bg-red-500/20 text-red-400',
    'Shopping': 'bg-pink-500/20 text-pink-400',
    'Travel': 'bg-cyan-500/20 text-cyan-400',
  }
  return categoryColors[category] || 'bg-gray-500/20 text-gray-400'
}

// Calculate percentage change
export const calculatePercentageChange = (current: number, previous: number): number => {
  if (previous === 0) return 0
  return ((current - previous) / Math.abs(previous)) * 100
}

// Parse CSV
export const parseCSV = (text: string): string[][] => {
  const lines = text.split('\n')
  return lines.map(line => line.split(',').map(cell => cell.trim()))
}

// Download file
export const downloadFile = (data: Blob, filename: string) => {
  const url = window.URL.createObjectURL(data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}

// Format file size
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}
