import axios, { AxiosInstance, AxiosRequestConfig } from 'axios'

export interface ApiError {
  message: string
  status?: number
  code?: string
}

class ApiClient {
  private client: AxiosInstance
  private baseURL: string

  constructor(baseURL: string = import.meta.env.VITE_API_URL || 'http://localhost:5000/api/v1') {
    this.baseURL = baseURL
    this.client = axios.create({
      baseURL,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
      },
    })

    // Response interceptor for error handling
    this.client.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response) {
          // Server responded with error status
          const apiError: ApiError = {
            message: error.response.data?.message || error.response.statusText,
            status: error.response.status,
            code: error.response.data?.code,
          }
          return Promise.reject(apiError)
        } else if (error.request) {
          // Request made but no response
          return Promise.reject({
            message: 'No response from server',
            code: 'NO_RESPONSE',
          })
        } else {
          return Promise.reject({
            message: error.message,
            code: 'UNKNOWN_ERROR',
          })
        }
      }
    )
  }

  // Household
  async getHousehold(id: number) {
    return this.client.get(`/household/${id}`)
  }

  async createHousehold(data: any) {
    return this.client.post('/household/create', data)
  }

  async updateHousehold(id: number, data: any) {
    return this.client.put(`/household/${id}`, data)
  }

  // Expenses
  async getTransactions(params?: any) {
    return this.client.get('/expenses/transactions', { params })
  }

  async createTransaction(data: any) {
    return this.client.post('/expenses/transactions', data)
  }

  async updateTransaction(id: number, data: any) {
    return this.client.put(`/expenses/transactions/${id}`, data)
  }

  async deleteTransaction(id: number) {
    return this.client.delete(`/expenses/transactions/${id}`)
  }

  async getExpenseDashboard(params?: any) {
    return this.client.get('/expenses/dashboard', { params })
  }

  async getCategories() {
    return this.client.get('/expenses/categories')
  }

  async importCSV(file: File, bank?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (bank) formData.append('bank', bank)
    return this.client.post('/expenses/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  }

  // Meals
  async getMealPlans(params?: any) {
    return this.client.get('/meals/plans', { params })
  }

  async createMealPlan(data: any) {
    return this.client.post('/meals/plans', data)
  }

  async updateMealPlan(id: number, data: any) {
    return this.client.put(`/meals/plans/${id}`, data)
  }

  async deleteMealPlan(id: number) {
    return this.client.delete(`/meals/plans/${id}`)
  }

  async getRecipes(params?: any) {
    return this.client.get('/meals/recipes', { params })
  }

  async createRecipe(data: any) {
    return this.client.post('/meals/recipes', data)
  }

  async searchRecipes(query: string) {
    return this.client.get('/recipes/search', { params: { q: query } })
  }

  async scanRecipe(file: File) {
    const formData = new FormData()
    formData.append('image', file)
    return this.client.post('/recipes/scan', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  }

  // Shopping
  async getShoppingList(params?: any) {
    return this.client.get('/shopping-list', { params })
  }

  async addShoppingItem(data: any) {
    return this.client.post('/shopping-list/items', data)
  }

  async updateShoppingItem(id: number, data: any) {
    return this.client.put(`/shopping-list/items/${id}`, data)
  }

  async deleteShoppingItem(id: number) {
    return this.client.delete(`/shopping-list/items/${id}`)
  }

  // Backup & Export
  async exportBackup(params?: any) {
    return this.client.get('/backup/export', { params, responseType: 'blob' })
  }

  async importBackup(file: File) {
    const formData = new FormData()
    formData.append('backup', file)
    return this.client.post('/backup/restore', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  }

  // Health check
  async getHealth() {
    return this.client.get('/health')
  }

  // Receipt Scanner
  async parseReceiptText(text: string, mode: string = 'local', apiKey?: string) {
    return this.client.post('/receipt/parse/text', {
      text,
      mode,
      api_key: apiKey,
    })
  }

  async parseReceiptFile(file: File, mode: string = 'local', apiKey?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (mode !== 'local') {
      formData.append('mode', mode)
      if (apiKey) formData.append('api_key', apiKey)
    }
    return this.client.post('/receipt/parse', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  }

  async getReceiptModes() {
    return this.client.get('/receipt/modes')
  }

  async ocrImage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return this.client.post('/receipt/ocr', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  }
}

export const apiClient = new ApiClient()
export default apiClient
