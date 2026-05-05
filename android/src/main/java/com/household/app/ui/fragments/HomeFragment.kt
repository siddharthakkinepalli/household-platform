package com.household.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.navigation.NavigationBarView
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.household.app.R
import com.household.app.ui.viewmodels.HouseholdViewModel

class HomeFragment : Fragment() {

    private lateinit var textHouseholdName: TextView
    private lateinit var textMembersCount: TextView
    private lateinit var textCurrency: TextView
    private lateinit var textBackupStatus: TextView
    private lateinit var textLastBackup: TextView
    private lateinit var textIntegrationGrocery: TextView
    private lateinit var textIntegrationDocs: TextView
    private lateinit var textIntegrationMeals: TextView
    private lateinit var textTimeline1: TextView
    private lateinit var textTimeline2: TextView
    private lateinit var textTimeline3: TextView

    private val viewModel: HouseholdViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textHouseholdName = view.findViewById(R.id.text_household_name)
        textMembersCount = view.findViewById(R.id.text_members_count)
        textCurrency = view.findViewById(R.id.text_currency)
        textBackupStatus = view.findViewById(R.id.text_backup_status)
        textLastBackup = view.findViewById(R.id.text_last_backup)
        textIntegrationGrocery = view.findViewById(R.id.text_integration_grocery)
        textIntegrationDocs = view.findViewById(R.id.text_integration_docs)
        textIntegrationMeals = view.findViewById(R.id.text_integration_meals)
        textTimeline1 = view.findViewById(R.id.text_timeline_1)
        textTimeline2 = view.findViewById(R.id.text_timeline_2)
        textTimeline3 = view.findViewById(R.id.text_timeline_3)

        val buttonScan: Button = view.findViewById(R.id.button_scan)
        val buttonSpeak: Button = view.findViewById(R.id.button_speak)
        val buttonParse: Button = view.findViewById(R.id.button_parse)
        val buttonOpenExpenses: Button = view.findViewById(R.id.button_open_expenses)
        val buttonOpenMeals: Button = view.findViewById(R.id.button_open_meals)
        val buttonOpenParser: Button = view.findViewById(R.id.button_open_parser)
        val buttonOpenFamily: Button = view.findViewById(R.id.button_open_family)

        // Observe household profile
        viewModel.householdProfile.observe(viewLifecycleOwner, Observer { profile ->
            textHouseholdName.text = profile.name
            textMembersCount.text = "Members: ${profile.membersCount}"
            textCurrency.text = "Currency: ${profile.currency}"
        })

        // Observe backup status
        viewModel.backupStatus.observe(viewLifecycleOwner, Observer { status ->
            textBackupStatus.text = if (status.isEnabled) {
                "Local backups enabled"
            } else {
                "Backups disabled"
            }
            textLastBackup.text = "Last backup: ${viewModel.getLastBackupTimeFormatted()}"

            textIntegrationGrocery.text = "Grocery budgeting is connected to expense categories"
            textIntegrationDocs.text = "Recipe scanner and parser run fully on device"
            textIntegrationMeals.text = "Meal planning works offline with local storage"
        })

        viewModel.timelineLines.observe(viewLifecycleOwner, Observer { lines ->
            textTimeline1.text = lines.getOrNull(0) ?: "-"
            textTimeline2.text = lines.getOrNull(1) ?: "-"
            textTimeline3.text = lines.getOrNull(2) ?: "-"
        })

        viewModel.captureMessage.observe(viewLifecycleOwner, Observer { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        })

        buttonScan.setOnClickListener {
            viewModel.processCapture("scan")
        }
        buttonSpeak.setOnClickListener {
            viewModel.processCapture("voice")
        }
        buttonParse.setOnClickListener {
            viewModel.processCapture("email")
        }

        buttonOpenExpenses.setOnClickListener {
            openTab(R.id.expensesFragment)
        }
        buttonOpenMeals.setOnClickListener {
            openTab(R.id.mealsFragment)
        }
        buttonOpenParser.setOnClickListener {
            openTab(R.id.documentsFragment)
        }
        buttonOpenFamily.setOnClickListener {
            openTab(R.id.familyFragment)
        }

        viewModel.refreshTimeline()
    }

    private fun openTab(destinationId: Int) {
        val sideNav = requireActivity().findViewById<NavigationBarView>(R.id.side_navigation)
        sideNav.selectedItemId = destinationId
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
