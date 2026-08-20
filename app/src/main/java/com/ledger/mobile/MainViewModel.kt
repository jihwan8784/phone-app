package com.ledger.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ledger.mobile.data.AccountEntity
import com.ledger.mobile.data.LedgerDao
import com.ledger.mobile.data.TransactionEntity
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainViewModel(private val dao: LedgerDao) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> = dao.accounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedAccountId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = selectedAccountId

    val transactions: StateFlow<List<TransactionEntity>> = selectedAccountId
        .flatMapLatest { id -> if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else dao.transactions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            accounts.collect { list ->
                if (selectedAccountId.value == null) selectedAccountId.value = list.firstOrNull()?.id
            }
        }
    }

    fun selectAccount(id: Long) { selectedAccountId.value = id }

    fun createAccount(name: String, password: String) = viewModelScope.launch {
        val id = dao.insertAccount(AccountEntity(name = name.trim(), passwordHash = hash(password)))
        selectedAccountId.value = id
    }

    fun deleteAccount(id: Long, password: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(dao.deleteAccount(id, hash(password)) == 1)
    }

    fun addTransaction(description: String, amount: Long, type: String) = viewModelScope.launch {
        selectedAccountId.value?.let { id ->
            dao.insertTransaction(TransactionEntity(id, id, java.time.LocalDate.now().toString(), description, amount, type))
        }
    }

    fun importOcrText(text: String) = viewModelScope.launch {
        val accountId = selectedAccountId.value ?: return@launch
        parseStatement(text).forEach { parsed ->
            dao.insertTransaction(
                TransactionEntity(
                    accountId = accountId,
                    date = parsed.date,
                    description = parsed.description,
                    amount = parsed.amount,
                    type = parsed.type
                )
            )
        }
    }

    private data class Parsed(val date: String, val description: String, val amount: Long, val type: String)

    private fun parseStatement(text: String): List<Parsed> {
        val dateRegex = Regex("^(\\d{1,2})[./-](\\d{1,2})$")
        val amountRegex = Regex("^(-)?([0-9][0-9,]*)\\s*원.*$")
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<Parsed>()
        var date: String? = null
        var description = ""
        for (line in lines) {
            val dateMatch = dateRegex.matchEntire(line)
            if (dateMatch != null) {
                date = runCatching {
                    LocalDate.of(LocalDate.now().year, dateMatch.groupValues[1].toInt(), dateMatch.groupValues[2].toInt())
                        .format(DateTimeFormatter.ISO_LOCAL_DATE)
                }.getOrNull()
                description = ""
                continue
            }
            val amountMatch = amountRegex.matchEntire(line)
            if (amountMatch != null && date != null && description.isNotBlank()) {
                val amount = amountMatch.groupValues[2].replace(",", "").toLongOrNull() ?: continue
                result += Parsed(date!!, description, amount, if (amountMatch.groupValues[1].isNotEmpty()) "EXPENSE" else "INCOME")
                description = ""
            } else if (!line.matches(Regex("^[0-9,]+원$"))) {
                description = if (description.isBlank()) line else "$description $line"
            }
        }
        return result
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        fun factory(dao: LedgerDao): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(dao) as T
        }
    }
}
