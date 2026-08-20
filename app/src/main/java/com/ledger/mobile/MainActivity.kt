package com.ledger.mobile

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledger.mobile.data.LedgerDatabase
import com.ledger.mobile.ocr.StatementOcr
import androidx.compose.material3.ExperimentalMaterial3Api

class MainActivity : ComponentActivity() {
    private val database by lazy { LedgerDatabase.create(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ledgerViewModel: MainViewModel = viewModel(factory = MainViewModel.factory(database.dao()))
            LedgerTheme { LedgerScreen(ledgerViewModel) }
        }
    }
}

@Composable
private fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    var showCreate by remember { mutableStateOf(accounts.isEmpty()) }
    var showDelete by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("ALL") }
    var ocrText by remember { mutableStateOf("") }
    var ocrError by remember { mutableStateOf("") }
    val ocr = remember { StatementOcr() }
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            ocrError = ""
            ocr.read(context, uri, onText = { ocrText = it }, onError = { ocrError = "이미지를 읽지 못했어요." })
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("나의 용돈기입장") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("휴대폰에 안전하게 저장되는 가계부", style = MaterialTheme.typography.titleMedium)
            if (accounts.isNotEmpty()) {
                AccountSelector(accounts, selectedId, viewModel, onDelete = { showDelete = true })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("전체") })
                    FilterChip(selected = filter == "INCOME", onClick = { filter = "INCOME" }, label = { Text("수입") })
                    FilterChip(selected = filter == "EXPENSE", onClick = { filter = "EXPENSE" }, label = { Text("지출") })
                }
                transactions.filter { filter == "ALL" || it.type == filter }.forEach { transaction ->
                    Text("${transaction.date}  ${transaction.description}  ${if (transaction.type == "INCOME") "+" else "-"}${transaction.amount}원")
                }
                Button(onClick = { showCreate = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("계좌 추가") }
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("거래내역 사진에서 OCR 읽기")
                }
                if (ocrError.isNotBlank()) Text(ocrError, color = MaterialTheme.colorScheme.error)
                if (ocrText.isNotBlank()) {
                    Text("OCR 결과", style = MaterialTheme.typography.titleSmall)
                    Text(ocrText, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("계좌를 먼저 만들어 주세요.")
                Button(onClick = { showCreate = true }) { Text("첫 계좌 만들기") }
            }
        }
    }

    if (showCreate) CreateAccountDialog(onDismiss = { showCreate = false }, onCreate = { name, password -> viewModel.createAccount(name, password); showCreate = false })
    if (showDelete && selectedId != null) DeleteAccountDialog(onDismiss = { showDelete = false }, onDelete = { password -> viewModel.deleteAccount(selectedId!!, password) { showDelete = false } })
}

@Composable
private fun AccountSelector(accounts: List<com.ledger.mobile.data.AccountEntity>, selectedId: Long?, viewModel: MainViewModel, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId } ?: accounts.first()
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(selected.name) }
    if (expanded) accounts.forEach { account -> TextButton(onClick = { viewModel.selectAccount(account.id); expanded = false }, modifier = Modifier.fillMaxWidth()) { Text(account.name) } }
    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "계좌 삭제") }
}

@Composable
private fun CreateAccountDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("계좌 만들기") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("계좌 이름") }); OutlinedTextField(password, { password = it }, label = { Text("삭제 비밀번호") }) } }, confirmButton = { TextButton(onClick = { if (name.isNotBlank() && password.length >= 4) onCreate(name, password) }) { Text("만들기") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onDelete: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("계좌 삭제") }, text = { OutlinedTextField(password, { password = it }, label = { Text("비밀번호") }) }, confirmButton = { TextButton(onClick = { onDelete(password) }) { Text("삭제") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
