package org.najwa.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign

data class Palette(val bg: Color,val surface: Color,val text: Color,val secondary: Color,val border: Color,val green: Color,val mint: Color,val amber: Color,val selected: Color,val track: Color)
// Madar Al-Sakeenah: ink, jade and dawn-gold. Text/secondary tones are contrast-led.
val Dark=Palette(Color(0xFF071715),Color(0xFF112521),Color(0xFFF6F4EC),Color(0xFFA9C0B6),Color(0xFF2D4840),Color(0xFF3EB28C),Color(0xFFA5E8C4),Color(0xFFF0C787),Color(0xFF1B3B32),Color(0xFF29483D))
val Light=Palette(Color(0xFFF5F3EB),Color(0xFFFEFDF7),Color(0xFF19352E),Color(0xFF58756A),Color(0xFFD8E0D4),Color(0xFF15795C),Color(0xFF126D53),Color(0xFF81551E),Color(0xFFE6F1E8),Color(0xFFDCE7DE))
val LocalPalette=staticCompositionLocalOf { Dark }
@Composable fun Panel(modifier: Modifier=Modifier, radius: Int=28, selected: Boolean=false, content: @Composable ColumnScope.()->Unit) {
    val c=LocalPalette.current; val shape=RoundedCornerShape(radius.dp)
    Column(modifier.clip(shape).background(if(selected) c.selected else c.surface,shape).border(1.dp,if(selected)c.green else c.border,shape),content=content)
}
@Composable fun Action(label: String,modifier: Modifier=Modifier,primary: Boolean=false,onClick: ()->Unit) {
    val c=LocalPalette.current
    if(primary) Button(onClick=onClick,modifier=modifier.heightIn(min=50.dp),shape=RoundedCornerShape(18.dp)) {
        Text(uiText(label),fontSize=15.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
    } else OutlinedButton(onClick=onClick,modifier=modifier.heightIn(min=50.dp),shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,c.border)) {
        Text(uiText(label),color=c.text,fontSize=14.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center)
    }
}

@Composable fun NajwaTheme(dark: Boolean,content: @Composable ()->Unit) {
    val c=if(dark)Dark else Light
    val base=if(dark)darkColorScheme() else lightColorScheme()
    val scheme=base.copy(primary=if(dark)c.green else c.mint,onPrimary=if(dark)Color(0xFF071715) else Color.White,
        primaryContainer=c.selected,onPrimaryContainer=c.mint,secondary=c.mint,onSecondary=c.bg,
        secondaryContainer=c.selected,onSecondaryContainer=c.text,background=c.bg,onBackground=c.text,
        surface=c.surface,onSurface=c.text,surfaceVariant=c.selected,onSurfaceVariant=c.secondary,
        surfaceContainerHigh=c.surface,surfaceContainer=c.surface,surfaceContainerLow=c.surface,
        outline=c.border,outlineVariant=c.border,error=c.amber)
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val direction=if(android.text.TextUtils.getLayoutDirectionFromLocale(locale)==android.view.View.LAYOUT_DIRECTION_RTL)
        androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    CompositionLocalProvider(LocalPalette provides c,androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
        MaterialTheme(colorScheme=scheme,typography=najwaTypography(),content=content)
    }
}
@Composable fun Mark(modifier: Modifier=Modifier) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(org.najwa.app.R.drawable.ic_najwa_mark), null,
        modifier.size(36.dp), colorFilter=androidx.compose.ui.graphics.ColorFilter.tint(LocalPalette.current.mint)
    )
}
@Composable fun Glyph(name: String,color: Color=LocalPalette.current.secondary,modifier: Modifier=Modifier) {
    val rtl=androidx.compose.ui.platform.LocalLayoutDirection.current==androidx.compose.ui.unit.LayoutDirection.Rtl
    Canvas(modifier.size(22.dp)) {
        val unit=size.width/24; val stroke=1.8f*unit
        fun line(x: Float,y: Float,x2: Float,y2: Float) = drawLine(color,Offset(x*unit,y*unit),Offset(x2*unit,y2*unit),stroke,StrokeCap.Round)
        fun path(vararg points: Pair<Float,Float>) { val p=Path(); points.forEachIndexed { i,v -> if(i==0)p.moveTo(v.first*unit,v.second*unit) else p.lineTo(v.first*unit,v.second*unit) }; drawPath(p,color,style=Stroke(stroke,cap=StrokeCap.Round)) }
        when(name) {
            "home"->{path(3f to 11f,12f to 3f,21f to 11f); path(6f to 10f,6f to 21f,18f to 21f,18f to 10f)}
            "sound"->{path(3f to 9f,7f to 9f,12f to 5f,12f to 19f,7f to 15f,3f to 15f,3f to 9f); line(17f,9f,21f,15f);line(21f,9f,17f,15f)}
            "check"->path(5f to 12f,10f to 17f,20f to 7f)
            "back","chevron"->{
                val pointsRight=if(name=="back")rtl else !rtl
                val outer=if(pointsRight)9f else 15f
                val tip=24f-outer
                path(outer to 6f,tip to 12f,outer to 18f)
            }
            "down"->path(6f to 9f,12f to 15f,18f to 9f)
            "dot"->drawCircle(color,4.5f*unit)
            "widget"->{path(3f to 3f,10f to 3f,10f to 10f,3f to 10f,3f to 3f);path(14f to 3f,21f to 3f,21f to 10f,14f to 10f,14f to 3f);path(3f to 14f,10f to 14f,10f to 21f,3f to 21f,3f to 14f);path(14f to 14f,21f to 14f,21f to 21f,14f to 21f,14f to 14f)}
            "bell"->{path(5f to 17f,7f to 14f,7f to 8f,9f to 5f,15f to 5f,17f to 8f,17f to 14f,19f to 17f,5f to 17f);drawArc(color,0f,180f,false,Offset(10*unit,18*unit),Size(4*unit,3*unit),style=Stroke(stroke))}
            "palette"->{drawCircle(color,9*unit,style=Stroke(stroke));drawCircle(color,2*unit,Offset(9*unit,8*unit));drawCircle(color,2*unit,Offset(16*unit,10*unit));drawCircle(color,2*unit,Offset(10*unit,16*unit))}
            "sun","settings"->{drawCircle(color,4*unit,style=Stroke(stroke)); repeat(8) { i->val a=i*Math.PI/4; line((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat()) }}
            "pin"->{val p=Path();p.moveTo(12*unit,22*unit);p.cubicTo(1*unit,11*unit,5*unit,3*unit,12*unit,3*unit);p.cubicTo(19*unit,3*unit,23*unit,11*unit,12*unit,22*unit);drawPath(p,color,style=Stroke(stroke));drawCircle(color,2.5f*unit,Offset(12*unit,10*unit),style=Stroke(stroke))}
            "shield"->{path(12f to 3f,20f to 7f,19f to 15f,12f to 22f,5f to 15f,4f to 7f,12f to 3f);path(8f to 12f,11f to 15f,16f to 10f)}
            "restore"->{drawArc(color,-40f,300f,false,Offset(3*unit,3*unit),Size(18*unit,18*unit),style=Stroke(stroke,cap=StrokeCap.Round));path(3f to 21f,3f to 14f,10f to 14f)}
            else->{drawCircle(color,9*unit,style=Stroke(stroke));line(12f,6f,12f,12f);line(12f,12f,16f,15f)}
        }
    }
}
