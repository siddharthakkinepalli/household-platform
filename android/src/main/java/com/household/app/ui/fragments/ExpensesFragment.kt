package com.household.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.household.app.R
import com.household.app.ui.viewmodels.ExpensesViewModel
import com.household.app.ui.viewmodels.Transaction

class ExpensesFragment : Fragment() {

    private lateinit var recyclerTransactions: RecyclerView
    private lateinit var recyclerCategories: RecyclerView
    private lateinit var buttonRefresh: Button
    private lateinit var rootView: View

    private val viewModel: ExpensesViewModel by viewModels()
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_expenses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView = view
        recyclerTransactions = view.findViewById(R.id.recycler_transactions)
        recyclerCategories = view.findViewById(R.id.recycler_categories)
        buttonRefresh = view.findViewById(R.id.button_refresh)

        // Setup recent transactions RecyclerView
        transactionAdapter = TransactionAdapter()
        recyclerTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }

        // Setup category summary RecyclerView
        categoryAdapter = CategoryAdapter()
        recyclerCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }

        // Observe recent transactions
        viewModel.recentTransactions.observe(viewLifecycleOwner, Observer { transactions ->
            transactionAdapter.submitList(transactions)
        })

        // Observe category summary
        viewModel.categorySummary.observe(viewLifecycleOwner, Observer { categories ->
            categoryAdapter.submitList(categories)
        })

        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            if (!error.isNullOrBlank()) {
                Snackbar.make(rootView, error, Snackbar.LENGTH_LONG).show()
            }
        })

        // Refresh button
        buttonRefresh.setOnClickListener {
            viewModel.refreshTransactions()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}

class TransactionAdapter : androidx.recyclerview.widget.ListAdapter<Transaction, TransactionViewHolder>(
    TransactionDiffCallback()
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = android.widget.FrameLayout(parent.context)
        binding.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class TransactionViewHolder(private val container: android.widget.FrameLayout) :
    androidx.recyclerview.widget.RecyclerView.ViewHolder(container) {
    fun bind(transaction: Transaction) {
        // Simple text display
        val textView = android.widget.TextView(container.context)
        textView.text = "${transaction.description} - \$${String.format("%.2f", transaction.amount)} (${transaction.category})"
        textView.textSize = 14f
        textView.setPadding(16, 16, 16, 16)
        container.removeAllViews()
        container.addView(textView)
    }
}

class TransactionDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Transaction>() {
    override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem == newItem
    }
}

class CategoryAdapter : androidx.recyclerview.widget.ListAdapter<com.household.app.ui.viewmodels.CategorySummary, CategoryViewHolder>(
    CategoryDiffCallback()
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = android.widget.FrameLayout(parent.context)
        binding.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class CategoryViewHolder(private val container: android.widget.FrameLayout) :
    androidx.recyclerview.widget.RecyclerView.ViewHolder(container) {
    fun bind(category: com.household.app.ui.viewmodels.CategorySummary) {
        val textView = android.widget.TextView(container.context)
        textView.text = "${category.category}: \$${String.format("%.2f", category.totalAmount)} (${category.transactionCount} txns)"
        textView.textSize = 14f
        textView.setPadding(16, 16, 16, 16)
        container.removeAllViews()
        container.addView(textView)
    }
}

class CategoryDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<com.household.app.ui.viewmodels.CategorySummary>() {
    override fun areItemsTheSame(oldItem: com.household.app.ui.viewmodels.CategorySummary, newItem: com.household.app.ui.viewmodels.CategorySummary): Boolean {
        return oldItem.category == newItem.category
    }

    override fun areContentsTheSame(oldItem: com.household.app.ui.viewmodels.CategorySummary, newItem: com.household.app.ui.viewmodels.CategorySummary): Boolean {
        return oldItem == newItem
    }
}
