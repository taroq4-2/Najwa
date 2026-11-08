package org.najwa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

@Composable fun PageHeading(title: String, subtitle: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, color=LocalPalette.current.text, fontSize=28.sp, fontWeight=FontWeight.Bold)
    Text(subtitle, color=LocalPalette.current.secondary, fontSize=14.sp, modifier=Modifier.padding(top=6.dp,bottom=22.dp))
}

@Composable fun SettingsCard(title: String, subtitle: String?=null, content: @Composable ColumnScope.()->Unit) {
    val c=LocalPalette.current
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,color=c.text,fontWeight=FontWeight.Bold,fontSize=17.sp)
            if(subtitle!=null)Text(subtitle,color=c.secondary,fontSize=13.sp)
            content()
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable fun OptionButton(label: String, selected: Boolean, modifier: Modifier=Modifier, onClick: ()->Unit) {
    val c=LocalPalette.current
    Surface(onClick=onClick,modifier=modifier.heightIn(min=48.dp).semantics { this.selected=selected },
        shape=RoundedCornerShape(14.dp), color=if(selected)c.selected else c.surface,
        border=BorderStroke(1.dp,if(selected)c.green else c.border)) {
        Text(label,modifier=Modifier.padding(horizontal=10.dp,vertical=13.dp),textAlign=TextAlign.Center,
            fontSize=14.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,color=if(selected)c.mint else c.text)
    }
}

@Composable fun <T> OptionGrid(items: List<T>,value: T,label: (T)->String,columns: Int=3,onChange: (T)->Unit) {
    val count=if(columns>2 && androidx.compose.ui.platform.LocalDensity.current.fontScale>1.25f)2 else columns
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items.chunked(count).forEach { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                row.forEach { item -> OptionButton(label(item),item==value,Modifier.weight(1f)) {onChange(item)} }
                repeat(count-row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable fun <T> Choice(value: T,items: List<T>,label: (T)->String,onChange: (T)->Unit) {
    var open by remember {mutableStateOf(false)}
    val c=LocalPalette.current
    Surface(onClick={open=true},shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,c.border),color=c.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(label(value),Modifier.weight(1f),color=c.text,fontSize=15.sp)
            Glyph("down",c.secondary)
        }
    }
    if(open) AlertDialog(onDismissRequest={open=false},title={Text("اختر الخيار المناسب")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items.forEach { item -> OptionButton(label(item),item==value,Modifier.fillMaxWidth()) {onChange(item);open=false} }
        }
    },confirmButton={TextButton(onClick={open=false}) {Text("إغلاق")}})
}

@Composable fun SectionLabel(text: String) {
    Text(text,color=LocalPalette.current.secondary,fontWeight=FontWeight.Medium,fontSize=12.sp,modifier=Modifier.padding(start=4.dp,top=6.dp,bottom=10.dp))
}
@Composable fun SettingsDivider() {HorizontalDivider(Modifier.padding(horizontal=18.dp),color=LocalPalette.current.border.copy(alpha=.55f))}
@Composable fun SettingsDestination(title: String,subtitle: String,icon: String,onClick: ()->Unit) {
    val c=LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=onClick).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).background(c.selected,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {Glyph(icon,c.mint)}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,color=c.text,fontWeight=FontWeight.Bold,fontSize=15.sp)
            Text(subtitle,color=c.secondary,fontSize=11.sp,lineHeight=18.sp)
        }
        Glyph("chevron",c.secondary,Modifier.size(16.dp))
    }
}
@Composable fun BackLink(label: String,onBack: ()->Unit) {
    TextButton(onClick=onBack,contentPadding=PaddingValues(vertical=8.dp)) {Glyph("back",modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(label)}
}
@Composable fun PreferenceSwitch(title: String,subtitle: String,checked: Boolean,onChange: (Boolean)->Unit) {
    val c=LocalPalette.current
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {Text(title,color=c.text,fontSize=15.sp,fontWeight=FontWeight.Bold);Text(subtitle,color=c.secondary,fontSize=12.sp)}
        Switch(checked,onChange,modifier=Modifier.semantics {contentDescription=title})
    }
}
@Composable fun ExpandableSettings(title: String,subtitle: String,initiallyExpanded: Boolean=false,content: @Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable {mutableStateOf(initiallyExpanded)}
    Panel(Modifier.fillMaxWidth().animateContentSize()) {
        SettingsDestination(title,subtitle,if(expanded)"down" else "chevron") {expanded=!expanded}
        if(expanded)Column(Modifier.padding(start=18.dp,end=18.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
    Spacer(Modifier.height(14.dp))
}
