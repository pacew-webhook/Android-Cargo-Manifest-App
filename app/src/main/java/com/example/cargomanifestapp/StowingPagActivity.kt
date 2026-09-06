package com.example.cargomanifestapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class StowingPagActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CargoRetroTheme { Surface { StowingPagScreen(onBack = { finish() }) } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StowingPagScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(StowingPagStorage.load(context)) }
    var noPag by remember { mutableStateOf("") }; var customer by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var pti by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PagInputMode.TOTAL) }
    var pcsText by remember { mutableStateOf("") }; var kgPerText by remember { mutableStateOf("") }; var totalText by remember { mutableStateOf("") }; var inputKg by remember { mutableStateOf("") }
    val weights = remember { mutableStateListOf<Double?>() }
    var editingId by remember { mutableStateOf<String?>(null) }

    fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    fun totalManual() = weights.filterNotNull().sum()
    fun reset() { noPag=""; customer=""; description=""; pti=""; pcsText=""; kgPerText=""; totalText=""; inputKg=""; weights.clear(); mode=PagInputMode.TOTAL; editingId=null }
    fun addKg(): Boolean { val v=inputKg.replace(',', '.').toDoubleOrNull(); if(v==null || v<=0) return false; val idx=weights.indexOfFirst { it==null }; if(idx>=0) weights[idx]=v else weights.add(v); inputKg=""; return true }
    fun saveItem() {
        val pcs = when(mode) { PagInputMode.MANUAL_KG -> weights.count { it != null }; else -> pcsText.toIntOrNull() ?: 0 }
        val total = when(mode) { PagInputMode.MANUAL_KG -> totalManual(); PagInputMode.KOLI_KG -> pcs * (kgPerText.replace(',', '.').toDoubleOrNull() ?: 0.0); PagInputMode.TOTAL -> totalText.replace(',', '.').toDoubleOrNull() ?: 0.0 }
        if(noPag.isBlank() || customer.isBlank() || description.isBlank() || pcs<=0 || total<=0) { Toast.makeText(context,"Lengkapi data PAG dan KG",Toast.LENGTH_SHORT).show(); return }
        val old = editingId?.let { id -> items.firstOrNull { it.id==id } }
        val item=StowingPagItem(id=old?.id ?: java.util.UUID.randomUUID().toString(), noPag=noPag.trim().uppercase(Locale.getDefault()), customer=customer.trim().uppercase(Locale.getDefault()), description=description.trim().uppercase(Locale.getDefault()), pti=pti.trim().uppercase(Locale.getDefault()), mode=mode, pcs=pcs, kgPerKoli=if(mode==PagInputMode.KOLI_KG) kgPerText.replace(',','.').toDoubleOrNull() else null, totalKg=total, weights=if(mode==PagInputMode.MANUAL_KG) weights.toList() else emptyList(), usedInStowing=old?.usedInStowing ?: false)
        items = if(old==null) items + item else items.map { if(it.id==item.id) item else it }; StowingPagStorage.save(context,items); Toast.makeText(context,"Data PAG Prepare disimpan",Toast.LENGTH_SHORT).show(); reset()
    }
    fun edit(x: StowingPagItem) { editingId=x.id; noPag=x.noPag; customer=x.customer; description=x.description; pti=x.pti; mode=x.mode; pcsText=x.pcs.toString(); kgPerText=x.kgPerKoli?.let(::fmt) ?: ""; totalText=fmt(x.totalKg); inputKg=""; weights.clear(); weights.addAll(x.weights) }

    Scaffold(topBar={ TopAppBar(title={Text("Form Stowing PAG Prepare",fontWeight=FontWeight.Bold)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,null)}}) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("Input PAG, Customer, Description & KG",fontWeight=FontWeight.Bold,fontSize=18.sp) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { UpperField(noPag,{noPag=it},"NO PAG",Modifier.weight(1f)); UpperField(customer,{customer=it},"Customer",Modifier.weight(1f)) } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { UpperField(description,{description=it},"Description",Modifier.weight(1f)); UpperField(pti,{pti=it},"PTI (opsional)",Modifier.weight(1f)) } }
            item { Text("METODE INPUT",fontWeight=FontWeight.Bold); Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) { PagInputMode.values().forEach { m -> FilterChip(selected=mode==m,onClick={mode=m},label={Text(when(m){PagInputMode.TOTAL->"TIMBANG TOTAL";PagInputMode.KOLI_KG->"KOLI × KG";PagInputMode.MANUAL_KG->"MANUAL KG"})},modifier=Modifier.weight(1f)) } } }
            item { when(mode) {
                PagInputMode.TOTAL -> Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { NumberField(pcsText,{pcsText=it},"KOLI / PCS"); NumberField(totalText,{totalText=it},"TOTAL KG") }
                PagInputMode.KOLI_KG -> { val total=(pcsText.toDoubleOrNull() ?: 0.0)*(kgPerText.replace(',','.').toDoubleOrNull() ?: 0.0); Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { NumberField(pcsText,{pcsText=it},"KOLI / PCS"); NumberField(kgPerText,{kgPerText=it},"KG / KOLI"); OutlinedTextField(value=fmt(total),onValueChange={},readOnly=true,label={Text("TOTAL KG (OTOMATIS)" )},modifier=Modifier.fillMaxWidth()) } }
                PagInputMode.MANUAL_KG -> Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) { NumberField(inputKg,{inputKg=it},"Input Berat (KG)",Modifier.weight(1f),KeyboardActions(onDone={if(!addKg()) Toast.makeText(context,"Masukkan KG valid",Toast.LENGTH_SHORT).show()})); Button(onClick={if(!addKg()) Toast.makeText(context,"Masukkan KG valid",Toast.LENGTH_SHORT).show()},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF381E72))){Icon(Icons.Default.Add,null);Text(" + KG")} }
                    if(weights.isNotEmpty()) { Text("Rincian Input KG (${weights.count{it!=null}} Koli):",fontWeight=FontWeight.SemiBold); LazyVerticalGrid(columns=GridCells.Fixed(5),modifier=Modifier.fillMaxWidth().heightIn(max=180.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){itemsIndexed(weights){i,w->if(w!=null) Row(Modifier.background(Color(0xFFE8DEF8),RoundedCornerShape(6.dp)).padding(4.dp),verticalAlignment=Alignment.CenterVertically){Text(fmt(w),Modifier.weight(1f)); IconButton(onClick={weights[i]=null},Modifier.size(22.dp)){Icon(Icons.Default.Delete,null,tint=Color.Red,modifier=Modifier.size(15.dp))}} else Spacer(Modifier.height(28.dp))}}; Text("PCS OTOMATIS: ${weights.count{it!=null}}     TOTAL: ${fmt(totalManual())} KG",fontWeight=FontWeight.Bold) }
                }
            } }
            item { Button(onClick=::saveItem,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=if(editingId==null) Color(0xFF2E7D32) else Color(0xFFE65100))){Text(if(editingId==null)"Simpan PAG Prepare" else "Update Data PAG Prepare") } }
            item { HorizontalDivider(); Text("Daftar PAG Prepare (${items.size})",fontWeight=FontWeight.Bold,fontSize=18.sp) }
            items(items,key={it.id}) { x -> Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xFFF4F1FA))){Column(Modifier.padding(12.dp)){Row{Column(Modifier.weight(1f)){Text("NO PAG: ${x.noPag}",fontWeight=FontWeight.Bold);Text("${x.description} - ${x.pcs} Koli (${fmt(x.totalKg)} KG)");Text("${x.customer}  ${if(x.pti.isBlank())"" else "• ${x.pti}"}",fontSize=12.sp);Text(when(x.mode){PagInputMode.TOTAL->"TIMBANG TOTAL";PagInputMode.KOLI_KG->"KOLI × KG";PagInputMode.MANUAL_KG->"MANUAL KG"},fontSize=11.sp,color=Color(0xFF555555));if(x.usedInStowing) Text("SUDAH MASUK STOWING",color=Color(0xFF2E7D32),fontWeight=FontWeight.Bold,fontSize=10.sp)};IconButton(onClick={edit(x)}){Icon(Icons.Default.Edit,null)};IconButton(onClick={items=items.filterNot{it.id==x.id};StowingPagStorage.save(context,items)}){Icon(Icons.Default.Delete,null,tint=Color.Red)}}}} }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable private fun UpperField(value:String,onChange:(String)->Unit,label:String,modifier:Modifier=Modifier){OutlinedTextField(value=value,onValueChange={onChange(it.uppercase())},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Characters),modifier=modifier)}
@Composable private fun NumberField(value:String,onChange:(String)->Unit,label:String,modifier:Modifier=Modifier,actions:KeyboardActions=KeyboardActions()){OutlinedTextField(value=value,onValueChange={onChange(it)},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),keyboardActions=actions,modifier=modifier.fillMaxWidth())}
