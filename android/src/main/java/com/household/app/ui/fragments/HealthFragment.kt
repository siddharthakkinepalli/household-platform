package com.household.app.ui.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.household.app.R
import com.household.app.data.DashboardPrefs
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

class HealthFragment : Fragment() {

    private data class FamilyMember(
        val name: String,
        val relation: String,
        val dob: LocalDate
    )

    private data class WeightLog(
        val memberName: String,
        val kg: Double,
        val date: LocalDate
    )

    private lateinit var editMemberName: EditText
    private lateinit var spinnerMemberRelation: Spinner
    private lateinit var editMemberDob: EditText
    private lateinit var spinnerWeightMember: Spinner
    private lateinit var editWeight: EditText
    private lateinit var editInsuranceExpiry: EditText
    private lateinit var textLatestWeight: TextView
    private lateinit var textInsuranceStatus: TextView
    private lateinit var familyContainer: LinearLayout
    private lateinit var weightsContainer: LinearLayout
    private lateinit var checkupsContainer: LinearLayout

    private val familyMembers = mutableListOf(
        FamilyMember("Self", "Self", LocalDate.of(1992, 1, 1)),
        FamilyMember("Spouse", "Spouse", LocalDate.of(1994, 5, 10)),
        FamilyMember("Kid 1", "Child", LocalDate.of(2020, 9, 2))
    )

    private val weightLogs = mutableListOf(
        WeightLog("Self", 78.4, LocalDate.now().minusDays(2)),
        WeightLog("Self", 78.2, LocalDate.now())
    )

    private var insuranceExpiry: LocalDate? = LocalDate.now().plusMonths(5)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_health, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editMemberName = view.findViewById(R.id.edit_member_name)
        spinnerMemberRelation = view.findViewById(R.id.spinner_member_relation)
        editMemberDob = view.findViewById(R.id.edit_member_dob)
        spinnerWeightMember = view.findViewById(R.id.spinner_weight_member)
        editWeight = view.findViewById(R.id.edit_weight)
        editInsuranceExpiry = view.findViewById(R.id.edit_insurance_expiry)
        textLatestWeight = view.findViewById(R.id.text_latest_weight)
        textInsuranceStatus = view.findViewById(R.id.text_insurance_status)
        familyContainer = view.findViewById(R.id.family_container)
        weightsContainer = view.findViewById(R.id.weights_container)
        checkupsContainer = view.findViewById(R.id.checkups_container)

        setupRelationSpinner()
        refreshWeightMemberSpinner()

        view.findViewById<Button>(R.id.button_add_member).setOnClickListener { addFamilyMember() }
        view.findViewById<Button>(R.id.button_add_weight).setOnClickListener { addWeightLog() }
        view.findViewById<Button>(R.id.button_set_insurance_expiry).setOnClickListener { updateInsuranceExpiry() }

        editInsuranceExpiry.setText(insuranceExpiry?.toString().orEmpty())
        renderFamilyMembers()
        renderWeights()
        renderInsuranceStatus()
        renderCheckups()
    }

    private fun setupRelationSpinner() {
        val relations = listOf("Self", "Spouse", "Child", "Parent", "Other")
        spinnerMemberRelation.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            relations
        )
    }

    private fun refreshWeightMemberSpinner() {
        val names = familyMembers.map { it.name }
        spinnerWeightMember.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            names
        )
    }

    private fun addFamilyMember() {
        val name = editMemberName.text?.toString().orEmpty().trim()
        val relation = spinnerMemberRelation.selectedItem?.toString().orEmpty().ifBlank { "Other" }
        val dob = parseDate(editMemberDob.text?.toString().orEmpty())

        if (name.isBlank() || dob == null) {
            Toast.makeText(requireContext(), "Enter name and DOB (YYYY-MM-DD)", Toast.LENGTH_SHORT).show()
            return
        }

        familyMembers.add(FamilyMember(name = name, relation = relation, dob = dob))
        editMemberName.setText("")
        editMemberDob.setText("")

        refreshWeightMemberSpinner()
        renderFamilyMembers()
        renderCheckups()
    }

    private fun addWeightLog() {
        val member = spinnerWeightMember.selectedItem?.toString().orEmpty()
        val kg = editWeight.text?.toString()?.toDoubleOrNull()

        if (member.isBlank() || kg == null || kg <= 0) {
            Toast.makeText(requireContext(), "Select member and enter valid weight", Toast.LENGTH_SHORT).show()
            return
        }

        weightLogs.add(WeightLog(memberName = member, kg = kg, date = LocalDate.now()))
        editWeight.setText("")
        renderWeights()
    }

    private fun updateInsuranceExpiry() {
        val parsed = parseDate(editInsuranceExpiry.text?.toString().orEmpty())
        if (parsed == null) {
            Toast.makeText(requireContext(), "Use date format YYYY-MM-DD", Toast.LENGTH_SHORT).show()
            return
        }

        insuranceExpiry = parsed
        renderInsuranceStatus()
    }

    private fun renderFamilyMembers() {
        familyContainer.removeAllViews()
        familyMembers.forEach { member ->
            val age = Period.between(member.dob, LocalDate.now()).years
            familyContainer.addView(cardLabel("${member.name} · ${member.relation} · age $age"))
        }
    }

    private fun renderWeights() {
        weightsContainer.removeAllViews()

        val latest = weightLogs.maxByOrNull { it.date }
        val sorted = weightLogs.sortedByDescending { it.date }
        val previous = sorted.getOrNull(1)
        textLatestWeight.text = if (latest == null) {
            "Latest weight: -"
        } else {
            "Latest weight: ${latest.memberName} ${latest.kg} kg (${latest.date})"
        }

        if (latest != null) {
            lifecycleScope.launch {
                DashboardPrefs.setWeightSnapshot(
                    context = requireContext(),
                    currentKg = latest.kg,
                    previousKg = previous?.kg,
                    date = latest.date
                )
            }
        }

        weightLogs.sortedByDescending { it.date }.take(8).forEach {
            weightsContainer.addView(cardLabel("${it.memberName}: ${it.kg} kg on ${it.date}"))
        }
    }

    private fun renderInsuranceStatus() {
        val expiry = insuranceExpiry
        if (expiry == null) {
            textInsuranceStatus.text = "Expiry status: not set"
            return
        }

        val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), expiry)
        textInsuranceStatus.text = if (daysLeft >= 0) {
            "TK expiry in $daysLeft days (${expiry})"
        } else {
            "TK expiry overdue by ${kotlin.math.abs(daysLeft)} days (${expiry})"
        }
    }

    private fun renderCheckups() {
        checkupsContainer.removeAllViews()

        val suggestions = mutableListOf<String>()
        suggestions.add("TK: Dental checkup every 6 to 12 months")

        familyMembers.forEach { member ->
            val age = Period.between(member.dob, LocalDate.now()).years
            when (member.relation) {
                "Child" -> {
                    suggestions.add("${member.name}: U-checkup planning with pediatrician (annual schedule)")
                    suggestions.add("${member.name}: Annual dental prevention visit")
                    suggestions.add("${member.name}: Vaccination review once per year")
                }
                else -> {
                    suggestions.add("${member.name}: General checkup every 3 years (age 18 to 34)")
                    if (age >= 35) {
                        suggestions.add("${member.name}: Health checkup every 3 years (Check-up 35)")
                        suggestions.add("${member.name}: Skin cancer screening every 2 years")
                    }
                    suggestions.add("${member.name}: Dental check every 6 to 12 months")
                }
            }
        }

        suggestions.distinct().forEach { tip ->
            checkupsContainer.addView(cardLabel(tip))
        }
    }

    private fun parseDate(raw: String): LocalDate? {
        return try {
            LocalDate.parse(raw.trim())
        } catch (_: Exception) {
            null
        }
    }

    private fun cardLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setBackgroundResource(R.drawable.bg_card)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(20, 16, 20, 16)
            gravity = Gravity.START
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            layoutParams = params
        }
    }
}
