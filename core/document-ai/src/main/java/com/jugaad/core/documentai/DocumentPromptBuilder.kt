package com.jugaad.core.documentai

import com.jugaad.core.documentai.model.DocumentType

object DocumentPromptBuilder {

    private const val SYSTEM_INSTRUCTION = "You are a document field extractor. Output valid JSON only. No explanation. No markdown. No code fences. If a field is not present in the document, omit it from the JSON entirely. Do not guess values you cannot see."

    fun build(ocrText: String, documentType: DocumentType): String {
        val schema = getSchema(documentType)
        val truncatedOcr = if (ocrText.length > 3000) ocrText.take(3000) else ocrText // Rough token estimate limit

        return """
            // Target JSON Schema: $schema
            $SYSTEM_INSTRUCTION
            
            <document_text>
            $truncatedOcr
            </document_text>
        """.trimIndent()
    }

    private fun getSchema(type: DocumentType): String = when (type) {
        DocumentType.RECEIPT -> "{ \"merchant\": \"string\", \"date\": \"string\", \"total_amount\": \"string\", \"currency\": \"string\", \"tax_amount\": \"string\", \"line_items\": [{\"name\": \"string\", \"price\": \"string\"}] }"
        DocumentType.INVOICE -> "{ \"vendor\": \"string\", \"invoice_number\": \"string\", \"date\": \"string\", \"due_date\": \"string\", \"total_amount\": \"string\", \"currency\": \"string\", \"iban\": \"string\", \"tax_number\": \"string\" }"
        DocumentType.PAYSLIP -> "{ \"employer\": \"string\", \"employee_name\": \"string\", \"pay_period\": \"string\", \"gross_salary\": \"string\", \"net_salary\": \"string\", \"tax_class\": \"string\", \"health_insurance\": \"string\" }"
        DocumentType.BANK_STATEMENT -> "{ \"bank_name\": \"string\", \"iban\": \"string\", \"period_start\": \"string\", \"period_end\": \"string\" }"
        DocumentType.PASSPORT -> "{ \"surname\": \"string\", \"given_names\": \"string\", \"nationality\": \"string\", \"dob\": \"string\", \"expiry_date\": \"string\", \"document_number\": \"string\", \"mrz_line1\": \"string\", \"mrz_line2\": \"string\" }"
        DocumentType.DRIVING_LICENCE -> "{ \"surname\": \"string\", \"given_names\": \"string\", \"dob\": \"string\", \"expiry_date\": \"string\", \"document_number\": \"string\" }"
        DocumentType.AUFENTHALTSTITEL -> "{ \"surname\": \"string\", \"given_names\": \"string\", \"nationality\": \"string\", \"dob\": \"string\", \"expiry_date\": \"string\", \"document_number\": \"string\", \"permit_type\": \"string\", \"issue_date\": \"string\" }"
        DocumentType.UTILITY_BILL -> "{ \"provider\": \"string\", \"account_number\": \"string\", \"billing_period\": \"string\", \"amount_due\": \"string\", \"due_date\": \"string\", \"iban\": \"string\" }"
        DocumentType.RENTAL_CONTRACT -> "{ \"landlord\": \"string\", \"tenant\": \"string\", \"address\": \"string\", \"monthly_rent\": \"string\", \"deposit\": \"string\", \"start_date\": \"string\", \"end_date\": \"string\" }"
        DocumentType.TAX_DOCUMENT -> "{ \"document_title\": \"string\", \"key_value_pairs\": [{\"key\": \"string\", \"value\": \"string\"}] }"
        DocumentType.UNKNOWN -> "{ \"document_title\": \"string\", \"key_value_pairs\": [{\"key\": \"string\", \"value\": \"string\"}] }"
    }
}
