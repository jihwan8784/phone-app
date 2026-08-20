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

class MainViewModel(private val dao: LedgerDao) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> = dao.accounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedAccountId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = selectedAccountId

    val transactions: StateFlow<List<TransactionEntity>> = selectedAccountId
        .flatMapLatest { id -> if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else dao.transactions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
