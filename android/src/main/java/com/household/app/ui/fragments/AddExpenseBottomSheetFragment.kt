package com.household.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.household.app.R

class AddExpenseBottomSheetFragment(
    private val categories: List<String>,
    private val tripOptions: List<String>,
    private val onExpenseAdded: (ExpenseEntry) -> Unit
) : BottomSheetDialogFragment() {

    data class ExpenseEntry(
        val amount: Double,
        val category: String,
        val trip: String?,
        val note: String,
        val paymentType: String
    )

    private lateinit var editAmount: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerTrip: Spinner
    private lateinit var editNote: EditText
    private lateinit var checkCash: CheckBox

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_add_expense, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editAmount = view.findViewById(R.id.edit_expense_amount)
        spinnerCategory = view.findViewById(R.id.spinner_expense_category)
        spinnerTrip = view.findViewById(R.id.spinner_expense_trip)
        editNote = view.findViewById(R.id.edit_expense_note)
        checkCash = view.findViewById(R.id.check_cash_payment)

        setupSpinners()

        view.findViewById<Button>(R.id.button_cancel_expense).setOnClickListener {
            dismiss()
        }

        view.findViewById<Button>(R.id.button_add_expense).setOnClickListener {
            saveExpense()
        }
    }

    private fun setupSpinners() {
        spinnerCategory.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )
        spinnerTrip.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            tripOptions
        )
    }

    private fun saveExpense() {
        val amountInput = editAmount.text?.toString().orEmpty().trim()
        if (amountInput.isEmpty()) {
            Toast.makeText(requireContext(), "Enter amount first", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountInput.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            Toast.makeText(requireContext(), "Enter a valid positive amount", Toast.LENGTH_SHORT).show()
            return
        }

        val category = spinnerCategory.selectedItem?.toString().orEmpty().ifBlank { "Other" }
        val tripName = spinnerTrip.selectedItem?.toString().orEmpty().takeIf { it.isNotBlank() && it != "No trip" }
        val paymentType = if (checkCash.isChecked) "Cash" else "Card"
        val note = editNote.text?.toString().orEmpty().trim()

        val entry = ExpenseEntry(
            amount = amount,
            category = category,
            trip = tripName,
            note = note,
            paymentType = paymentType
        )

        onExpenseAdded(entry)
        dismiss()
    }
}
