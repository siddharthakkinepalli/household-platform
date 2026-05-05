import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '@services/api_client'

// Household Hooks
export const useHousehold = (id: number) => {
  return useQuery({
    queryKey: ['household', id],
    queryFn: async () => {
      const { data } = await apiClient.getHousehold(id)
      return data
    },
  })
}

// Expenses Hooks
export const useTransactions = (params?: any) => {
  return useQuery({
    queryKey: ['transactions', params],
    queryFn: async () => {
      const { data } = await apiClient.getTransactions(params)
      return data
    },
  })
}

export const useExpenseDashboard = (params?: any) => {
  return useQuery({
    queryKey: ['expense-dashboard', params],
    queryFn: async () => {
      const { data } = await apiClient.getExpenseDashboard(params)
      return data
    },
  })
}

export const useCategories = () => {
  return useQuery({
    queryKey: ['categories'],
    queryFn: async () => {
      const { data } = await apiClient.getCategories()
      return data
    },
  })
}

export const useCreateTransaction = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: any) => apiClient.createTransaction(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['expense-dashboard'] })
    },
  })
}

export const useUpdateTransaction = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) =>
      apiClient.updateTransaction(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['expense-dashboard'] })
    },
  })
}

export const useDeleteTransaction = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiClient.deleteTransaction(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['expense-dashboard'] })
    },
  })
}

export const useImportCSV = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, bank }: { file: File; bank?: string }) =>
      apiClient.importCSV(file, bank),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['expense-dashboard'] })
    },
  })
}

// Meals Hooks
export const useMealPlans = (params?: any) => {
  return useQuery({
    queryKey: ['meal-plans', params],
    queryFn: async () => {
      const { data } = await apiClient.getMealPlans(params)
      return data
    },
  })
}

export const useRecipes = (params?: any) => {
  return useQuery({
    queryKey: ['recipes', params],
    queryFn: async () => {
      const { data } = await apiClient.getRecipes(params)
      return data
    },
  })
}

export const useSearchRecipes = (query: string) => {
  return useQuery({
    queryKey: ['recipes', 'search', query],
    queryFn: async () => {
      const { data } = await apiClient.searchRecipes(query)
      return data
    },
    enabled: query.length > 2,
  })
}

export const useCreateRecipe = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: any) => apiClient.createRecipe(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
    },
  })
}

export const useScanRecipe = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => apiClient.scanRecipe(file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
    },
  })
}

export const useCreateMealPlan = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: any) => apiClient.createMealPlan(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['meal-plans'] })
    },
  })
}

// Shopping Hooks
export const useShoppingList = (params?: any) => {
  return useQuery({
    queryKey: ['shopping-list', params],
    queryFn: async () => {
      const { data } = await apiClient.getShoppingList(params)
      return data
    },
  })
}

export const useAddShoppingItem = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: any) => apiClient.addShoppingItem(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shopping-list'] })
    },
  })
}

export const useUpdateShoppingItem = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) =>
      apiClient.updateShoppingItem(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shopping-list'] })
    },
  })
}

export const useDeleteShoppingItem = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiClient.deleteShoppingItem(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shopping-list'] })
    },
  })
}

// Backup Hooks
export const useExportBackup = () => {
  return useMutation({
    mutationFn: (params?: any) => apiClient.exportBackup(params),
  })
}

export const useImportBackup = () => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => apiClient.importBackup(file),
    onSuccess: () => {
      queryClient.invalidateQueries()
    },
  })
}
